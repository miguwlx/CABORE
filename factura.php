<?php
/**
 * factura.php — Genera la vista imprimible de una factura para un pedido.
 * Accesible solo por el administrador desde la pestaña "Facturas".
 */
session_start();
include("conexion.php");

if (!isset($_SESSION['usuario_id']) || !in_array($_SESSION['rol'], ['administrador', 'emprendedor'], true)) {
    header("Location: login.html"); exit();
}

$es_admin = $_SESSION['rol'] === 'administrador';
$usuario_sesion_id = (int) $_SESSION['usuario_id'];

$pedido_id = (int) ($_GET['id'] ?? 0);
if ($pedido_id <= 0) { die('Pedido inválido.'); }

// Si es emprendedor, obtenemos el id de su tienda para filtrar la factura
$tienda_emprendedor_id = 0;
if (!$es_admin) {
    $stmtTE = $conn->prepare("SELECT id FROM tiendas WHERE usuario_id = ?");
    $stmtTE->bind_param("i", $usuario_sesion_id);
    $stmtTE->execute();
    $tiendaE = $stmtTE->get_result()->fetch_assoc();
    if (!$tiendaE) { die('No tienes una tienda asociada.'); }
    $tienda_emprendedor_id = (int) $tiendaE['id'];
}

$stmtP = $conn->prepare("
    SELECT p.*, u.nombre AS cliente_nombre, u.correo AS cliente_correo, u.telefono AS cliente_telefono
    FROM pedidos p
    JOIN usuarios u ON u.id = p.usuario_id
    WHERE p.id = ?
");
$stmtP->bind_param("i", $pedido_id);
$stmtP->execute();
$pedido = $stmtP->get_result()->fetch_assoc();

if (!$pedido) { die('Pedido no encontrado.'); }

if ($es_admin) {
    $stmtI = $conn->prepare("
        SELECT pi.cantidad, pi.precio_unitario, pr.nombre AS producto_nombre, t.nombre AS tienda_nombre
        FROM pedido_items pi
        JOIN productos pr ON pr.id = pi.producto_id
        JOIN tiendas t ON t.id = pr.tienda_id
        WHERE pi.pedido_id = ?
    ");
    $stmtI->bind_param("i", $pedido_id);
} else {
    // El emprendedor solo ve los productos que pertenecen a SU tienda dentro de este pedido
    $stmtI = $conn->prepare("
        SELECT pi.cantidad, pi.precio_unitario, pr.nombre AS producto_nombre, t.nombre AS tienda_nombre
        FROM pedido_items pi
        JOIN productos pr ON pr.id = pi.producto_id
        JOIN tiendas t ON t.id = pr.tienda_id
        WHERE pi.pedido_id = ? AND pr.tienda_id = ?
    ");
    $stmtI->bind_param("ii", $pedido_id, $tienda_emprendedor_id);
}
$stmtI->execute();
$items = $stmtI->get_result()->fetch_all(MYSQLI_ASSOC);

if (!$es_admin && empty($items)) {
    die('Esta factura no tiene productos de tu tienda.');
}

$numero_factura = 'FAC-' . str_pad($pedido_id, 6, '0', STR_PAD_LEFT);
$subtotal = array_sum(array_map(fn($i) => $i['cantidad'] * $i['precio_unitario'], $items));
?>
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<title><?= $numero_factura ?> — Caboré</title>
<link href="https://fonts.googleapis.com/css2?family=Playfair+Display:wght@700;900&family=DM+Sans:wght@300;400;500;600&display=swap" rel="stylesheet">
<style>
:root { --gold:#b8923c; --dark:#1c1810; --muted:rgba(28,24,16,0.56); --border:rgba(184,146,60,0.28); }
*,*::before,*::after{margin:0;padding:0;box-sizing:border-box;}
body{font-family:'DM Sans',sans-serif;color:var(--dark);background:#faf7f0;padding:40px;}
.factura{max-width:720px;margin:0 auto;background:#fff;border:1px solid var(--border);border-radius:16px;padding:44px;}
.top{display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:32px;padding-bottom:24px;border-bottom:2px solid var(--border);}
.logo{font-family:'Playfair Display',serif;font-size:26px;font-weight:900;color:var(--gold);}
.logo span{color:var(--dark);}
.factura-num{text-align:right;}
.factura-num h1{font-family:'Playfair Display',serif;font-size:20px;}
.factura-num p{font-size:13px;color:var(--muted);}
.datos{display:grid;grid-template-columns:1fr 1fr;gap:24px;margin-bottom:28px;}
.datos h3{font-size:11px;text-transform:uppercase;letter-spacing:0.5px;color:var(--muted);margin-bottom:6px;}
.datos p{font-size:14px;margin-bottom:2px;}
table{width:100%;border-collapse:collapse;margin-bottom:24px;}
th{text-align:left;font-size:11px;text-transform:uppercase;letter-spacing:0.5px;color:var(--muted);padding:8px 10px;border-bottom:1px solid var(--border);}
td{padding:10px 10px;font-size:14px;border-bottom:1px solid rgba(28,24,16,0.07);}
.right{text-align:right;}
.totales{display:flex;justify-content:flex-end;}
.totales table{width:280px;}
.totales td{font-size:14px;}
.totales tr:last-child td{font-weight:700;font-size:16px;border-top:2px solid var(--border);border-bottom:none;padding-top:12px;}
.badge{display:inline-block;padding:4px 12px;border-radius:20px;font-size:12px;font-weight:700;}
.badge-pendiente{background:rgba(184,146,60,0.15);color:#a8832a;}
.badge-confirmado{background:rgba(37,99,235,0.12);color:#2563eb;}
.badge-enviado{background:rgba(147,51,234,0.12);color:#9333ea;}
.badge-entregado{background:rgba(15,157,99,0.12);color:#0c7a4d;}
.badge-cancelado{background:rgba(220,38,38,0.1);color:#b91c1c;}
.acciones{max-width:720px;margin:0 auto 16px;display:flex;gap:10px;justify-content:flex-end;}
.btn{padding:10px 20px;border-radius:10px;border:1px solid var(--border);background:var(--gold);color:#fff;font-weight:600;cursor:pointer;font-family:'DM Sans',sans-serif;text-decoration:none;font-size:13px;display:inline-block;}
.btn-outline{background:transparent;color:var(--gold);}
footer{margin-top:32px;padding-top:20px;border-top:1px solid var(--border);text-align:center;color:var(--muted);font-size:12px;}
@media print { .acciones{display:none;} body{background:#fff;padding:0;} .factura{border:none;} }
</style>
</head>
<body>

<div class="acciones">
    <button class="btn" onclick="window.print()">🖨️ Imprimir / Guardar PDF</button>
    <a href="admin.php" class="btn btn-outline">← Volver al panel</a>
</div>

<div class="factura">
    <div class="top">
        <div class="logo">Cabo<span>ré</span></div>
        <div class="factura-num">
            <h1><?= $numero_factura ?></h1>
            <p>Fecha: <?= date('d/m/Y H:i', strtotime($pedido['creado_en'])) ?></p>
            <p><span class="badge badge-<?= htmlspecialchars($pedido['estado']) ?>"><?= htmlspecialchars(ucfirst($pedido['estado'])) ?></span></p>
        </div>
    </div>

    <div class="datos">
        <div>
            <h3>Cliente</h3>
            <p><strong><?= htmlspecialchars($pedido['cliente_nombre']) ?></strong></p>
            <p><?= htmlspecialchars($pedido['cliente_correo']) ?></p>
            <?php if (!empty($pedido['cliente_telefono'])): ?><p><?= htmlspecialchars($pedido['cliente_telefono']) ?></p><?php endif; ?>
        </div>
        <div>
            <h3>Entrega</h3>
            <p><?= htmlspecialchars($pedido['direccion'] ?? '—') ?></p>
            <?php if (!empty($pedido['notas'])): ?><p style="color:var(--muted)">Notas: <?= htmlspecialchars($pedido['notas']) ?></p><?php endif; ?>
        </div>
    </div>

    <table>
        <thead>
            <tr>
                <th>Producto</th>
                <th>Tienda</th>
                <th class="right">Cant.</th>
                <th class="right">Precio unit.</th>
                <th class="right">Subtotal</th>
            </tr>
        </thead>
        <tbody>
            <?php foreach ($items as $it): ?>
            <tr>
                <td><?= htmlspecialchars($it['producto_nombre']) ?></td>
                <td><?= htmlspecialchars($it['tienda_nombre']) ?></td>
                <td class="right"><?= $it['cantidad'] ?></td>
                <td class="right">$<?= number_format($it['precio_unitario'], 0, ',', '.') ?></td>
                <td class="right">$<?= number_format($it['cantidad'] * $it['precio_unitario'], 0, ',', '.') ?></td>
            </tr>
            <?php endforeach; ?>
        </tbody>
    </table>

    <div class="totales">
        <table>
            <tr><td>Subtotal</td><td class="right">$<?= number_format($subtotal, 0, ',', '.') ?></td></tr>
            <tr><td>Total</td><td class="right">$<?= number_format($pedido['total'], 0, ',', '.') ?></td></tr>
        </table>
    </div>

    <footer>Factura generada por el panel de administración de Caboré · <?= $numero_factura ?></footer>
</div>

</body>
</html>