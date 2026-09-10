<?php
session_start();
include("conexion.php");

// Protección de acceso
if (!isset($_SESSION['usuario_id']) || $_SESSION['rol'] !== 'emprendedor') {
    header("Location: login.html"); exit();
}

$usuario_id = (int)$_SESSION['usuario_id'];

// Datos del usuario
$stmtU = $conn->prepare("SELECT nombre, foto, ciudad, telefono FROM usuarios WHERE id = ?");
$stmtU->bind_param("i", $usuario_id);
$stmtU->execute();
$user   = $stmtU->get_result()->fetch_assoc();
$nombre = htmlspecialchars($user['nombre']);

// Crear tienda automáticamente si no existe
$stmtC = $conn->prepare("SELECT id FROM tiendas WHERE usuario_id = ?");
$stmtC->bind_param("i", $usuario_id);
$stmtC->execute();
if ($stmtC->get_result()->num_rows === 0) {
    $ins = $conn->prepare("INSERT INTO tiendas (usuario_id, nombre, descripcion, color) VALUES (?, 'Mi tienda', 'Descripción de mi tienda', '#c9a84c')");
    $ins->bind_param("i", $usuario_id);
    $ins->execute();
}

// Traer tienda
$stmtT = $conn->prepare("SELECT * FROM tiendas WHERE usuario_id = ?");
$stmtT->bind_param("i", $usuario_id);
$stmtT->execute();
$tienda    = $stmtT->get_result()->fetch_assoc();
$tienda_id = (int)$tienda['id'];

// Categorías
$categorias = $conn->query("SELECT * FROM categorias ORDER BY id");

// Productos
$stmtP = $conn->prepare(
    "SELECT p.*, c.nombre AS cat_nombre, c.icono AS cat_icono
     FROM productos p
     LEFT JOIN categorias c ON p.categoria_id = c.id
     WHERE p.tienda_id = ?
     ORDER BY p.id DESC"
);
$stmtP->bind_param("i", $tienda_id);
$stmtP->execute();
$res_productos = $stmtP->get_result();
$productos     = $res_productos->fetch_all(MYSQLI_ASSOC);

// Stats
$totalProductos  = count($productos);
$totalActivos    = count(array_filter($productos, fn($p) => $p['activo'] == 1));
$totalDestacados = count(array_filter($productos, fn($p) => $p['destacado'] == 1));
$valorInventario = array_sum(array_map(fn($p) => $p['precio'] * $p['stock'], $productos));

// ══════════ VENTAS / ESTADÍSTICAS DE COMPRAS ══════════
// Trae cada ítem vendido de pedidos que incluyan productos de esta tienda,
// junto con los datos del pedido y del comprador.
$stmtV = $conn->prepare("
    SELECT pi.id AS item_id, pi.cantidad, pi.precio_unitario, pi.producto_id,
           p.id AS pedido_id, p.estado, p.creado_en, p.direccion,
           pr.nombre AS producto_nombre, pr.imagen AS producto_imagen,
           u.nombre AS comprador_nombre, u.telefono AS comprador_telefono
    FROM pedido_items pi
    JOIN pedidos p    ON p.id = pi.pedido_id
    JOIN productos pr ON pr.id = pi.producto_id
    JOIN usuarios u   ON u.id = p.usuario_id
    WHERE pr.tienda_id = ?
    ORDER BY p.creado_en DESC
");
$stmtV->bind_param("i", $tienda_id);
$stmtV->execute();
$ventas = $stmtV->get_result()->fetch_all(MYSQLI_ASSOC);

// Estadísticas de ventas
$totalVentas          = 0;
$pedidosIds           = [];
$pedidosPendientesIds = [];
$ventasPorDia         = []; // para el mini gráfico de los últimos 7 días

foreach ($ventas as $v) {
    $subtotal        = $v['cantidad'] * $v['precio_unitario'];
    $totalVentas     += $subtotal;
    $pedidosIds[$v['pedido_id']] = true;
    if ($v['estado'] === 'pendiente') {
        $pedidosPendientesIds[$v['pedido_id']] = true;
    }
    $dia = substr($v['creado_en'], 0, 10);
    $ventasPorDia[$dia] = ($ventasPorDia[$dia] ?? 0) + $subtotal;
}

$totalPedidosVenta = count($pedidosIds);
$totalPendientes   = count($pedidosPendientesIds);
$ticketPromedio    = $totalPedidosVenta > 0 ? $totalVentas / $totalPedidosVenta : 0;

// Últimos 7 días (para el mini gráfico de barras)
$ultimos7dias = [];
for ($i = 6; $i >= 0; $i--) {
    $dia = date('Y-m-d', strtotime("-$i days"));
    $ultimos7dias[] = ['fecha' => $dia, 'label' => date('D', strtotime($dia)), 'total' => $ventasPorDia[$dia] ?? 0];
}
$maxVentaDia = max(array_column($ultimos7dias, 'total')) ?: 1;

// Productos más vendidos (top 5)
$masVendidos = [];
foreach ($ventas as $v) {
    $pid = $v['producto_id'];
    if (!isset($masVendidos[$pid])) {
        $masVendidos[$pid] = ['nombre' => $v['producto_nombre'], 'imagen' => $v['producto_imagen'], 'cantidad' => 0, 'total' => 0];
    }
    $masVendidos[$pid]['cantidad'] += $v['cantidad'];
    $masVendidos[$pid]['total']    += $v['cantidad'] * $v['precio_unitario'];
}
usort($masVendidos, fn($a, $b) => $b['cantidad'] <=> $a['cantidad']);
$masVendidos = array_slice($masVendidos, 0, 5);

// Agrupar por pedido para la tabla de ventas (un pedido puede tener varios ítems de esta tienda)
$pedidosAgrupados = [];
foreach ($ventas as $v) {
    $pid = $v['pedido_id'];
    if (!isset($pedidosAgrupados[$pid])) {
        $pedidosAgrupados[$pid] = [
            'pedido_id'  => $pid,
            'estado'     => $v['estado'],
            'creado_en'  => $v['creado_en'],
            'comprador'  => $v['comprador_nombre'],
            'telefono'   => $v['comprador_telefono'],
            'direccion'  => $v['direccion'],
            'items'      => [],
            'total'      => 0,
        ];
    }
    $pedidosAgrupados[$pid]['items'][] = $v;
    $pedidosAgrupados[$pid]['total']  += $v['cantidad'] * $v['precio_unitario'];
}
$pedidosAgrupados = array_values($pedidosAgrupados);
usort($pedidosAgrupados, fn($a, $b) => strtotime($b['creado_en']) <=> strtotime($a['creado_en']));

// Mensaje flash
$flash = $_SESSION['flash'] ?? null;
unset($_SESSION['flash']);
?>
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Caboré — Panel Emprendedor</title>
<link href="https://fonts.googleapis.com/css2?family=Playfair+Display:wght@700;900&family=DM+Sans:wght@300;400;500;600&display=swap" rel="stylesheet">
<link rel="stylesheet" href="css/emprendedor.css">
<style>
/* ── EXTRAS EMPRENDEDOR v2 ── */
.badge {
    display:inline-flex; align-items:center; gap:5px;
    padding:3px 10px; border-radius:20px; font-size:11px; font-weight:700;
}
.badge-green  { background:rgba(15,157,99,0.12);  color:#0c7a4d; border:1px solid rgba(15,157,99,0.28); }
.badge-gold   { background:rgba(184,146,60,0.14);  color:var(--gold);  border:1px solid var(--border); }
.badge-red    { background:rgba(220,38,38,0.10);   color:#b91c1c; border:1px solid rgba(220,38,38,0.25); }
.badge-gray   { background:rgba(28,24,16,0.05); color:var(--muted); border:1px solid rgba(28,24,16,0.10); }

/* Selector de estado del pedido (pestaña Ventas) */
.estado-select {
    padding:6px 10px;
    border-radius:8px;
    border:1.5px solid var(--border);
    background:var(--mid);
    font-family:'DM Sans',sans-serif;
    font-size:12.5px;
    font-weight:600;
    color:var(--text);
    cursor:pointer;
}
.estado-select:focus { outline:none; border-color:var(--gold); }
.prod-table td { vertical-align:top; }

/* Preview tienda */
.store-banner-preview {
    width:100%; height:140px;
    border-radius:14px 14px 0 0;
    object-fit:cover;
    background: linear-gradient(135deg, #fdf6e8, #fbeed4);
    display:flex; align-items:center; justify-content:center;
    font-size:32px; color:var(--muted);
    border:1px solid var(--border);
    overflow:hidden;
}
.store-banner-preview img { width:100%; height:100%; object-fit:cover; }

.store-card-preview {
    background:var(--card);
    border:1px solid var(--border);
    border-radius:14px;
    overflow:hidden;
    margin-bottom:20px;
}
.store-card-inner {
    padding:20px;
    display:flex;
    align-items:center;
    gap:16px;
}
.store-logo-preview {
    width:64px; height:64px;
    border-radius:14px;
    object-fit:cover;
    background:var(--mid);
    border:2px solid var(--border);
    display:flex; align-items:center; justify-content:center;
    font-size:28px;
    flex-shrink:0;
}
.store-info-preview h3 { font-size:17px; font-weight:700; margin-bottom:4px; }
.store-info-preview p  { color:var(--muted); font-size:13px; }

.color-swatch {
    display:inline-block;
    width:14px; height:14px;
    border-radius:50%;
    border:2px solid rgba(28,24,16,0.18);
    vertical-align:middle;
    margin-right:6px;
}

/* Tabs dentro de sección tienda */
.tab-bar {
    display:flex;
    gap:8px;
    margin-bottom:24px;
    border-bottom:1px solid var(--border);
    padding-bottom:0;
}
.tab-btn {
    padding:10px 18px;
    background:transparent;
    border:none;
    border-bottom:2px solid transparent;
    color:var(--muted);
    font-family:'DM Sans',sans-serif;
    font-size:14px;
    font-weight:600;
    cursor:pointer;
    transition:all 0.2s;
    margin-bottom:-1px;
}
.tab-btn.active { color:var(--gold); border-bottom-color:var(--gold); }
.tab-panel { display:none; }
.tab-panel.active { display:block; animation:fadeUp 0.35s ease both; }

/* Tabla de productos */
.prod-table {
    width:100%;
    border-collapse:collapse;
}
.prod-table th {
    text-align:left;
    font-size:11px;
    font-weight:700;
    color:var(--muted);
    text-transform:uppercase;
    letter-spacing:0.5px;
    padding:10px 14px;
    border-bottom:1px solid var(--border);
}
.prod-table td {
    padding:14px;
    border-bottom:1px solid rgba(28,24,16,0.07);
    font-size:14px;
    vertical-align:middle;
}
.prod-table tr:last-child td { border-bottom:none; }
.prod-table tr:hover td { background:rgba(28,24,16,0.025); }

.prod-thumb {
    width:48px; height:48px;
    border-radius:10px;
    object-fit:cover;
    background:var(--mid);
    display:flex; align-items:center; justify-content:center;
    font-size:22px;
    flex-shrink:0;
}

/* Stock indicator */
.stock-bar {
    height:4px;
    background:rgba(28,24,16,0.08);
    border-radius:2px;
    margin-top:5px;
    overflow:hidden;
}
.stock-fill {
    height:100%;
    border-radius:2px;
    background:linear-gradient(90deg, #0c7a4d, #0f9d63);
    transition:width 0.5s;
}

/* Flash message */
.flash {
    padding:14px 20px;
    border-radius:12px;
    font-size:14px;
    font-weight:500;
    margin-bottom:20px;
    display:flex; align-items:center; gap:10px;
}
.flash-ok  { background:rgba(15,157,99,0.10); border:1px solid rgba(15,157,99,0.28); color:#0c7a4d; }
.flash-err { background:rgba(220,38,38,0.10);  border:1px solid rgba(220,38,38,0.25);  color:#b91c1c; }

/* Links redes sociales */
.social-links {
    display:flex; gap:10px; flex-wrap:wrap; margin-top:10px;
}
.social-chip {
    display:inline-flex; align-items:center; gap:6px;
    padding:5px 12px;
    background:rgba(28,24,16,0.04);
    border:1px solid var(--border);
    border-radius:20px;
    font-size:12px; color:var(--muted);
    text-decoration:none;
}
.social-chip:hover { color:var(--gold); border-color:var(--border); }

/* Imagen upload preview */
.img-upload-area {
    border:2px dashed rgba(28,24,16,0.18);
    border-radius:12px;
    padding:24px;
    text-align:center;
    cursor:pointer;
    transition:all 0.3s;
    color:var(--muted);
    font-size:13px;
}
.img-upload-area:hover { border-color:var(--gold); color:var(--gold); background:rgba(201,168,76,0.04); }
.img-upload-area input[type=file] {
    position:absolute; opacity:0; width:0; height:0;
}

/* Oferta badge en tabla */
.oferta-tag {
    font-size:11px;
    color:#0c7a4d;
    display:block;
    margin-top:2px;
}

/* Vista grilla vs tabla toggle */
.view-toggle { display:flex; gap:6px; }
.view-btn {
    width:32px; height:32px;
    background:rgba(28,24,16,0.04);
    border:1px solid var(--border);
    border-radius:8px;
    cursor:pointer;
    font-size:14px;
    display:flex; align-items:center; justify-content:center;
    color:var(--muted);
    transition:all 0.2s;
}
.view-btn.active { background:rgba(184,146,60,0.14); color:var(--gold); border-color:var(--border); }
</style>
</head>
<body>
<div class="bg-grid"></div>

<!-- ══════════════════════ SIDEBAR ══════════════════════ -->
<div class="sidebar">
    <div class="sb-logo">Cabo<span>ré</span></div>

    <div class="sb-user">
        <div class="sb-avatar">🚀</div>
        <div class="sb-user-name"><?= $nombre ?></div>
        <div class="sb-user-role">Emprendedor</div>
    </div>

    <nav class="sb-nav">
        <div class="sb-item active" data-tab="inicio" onclick="irA('inicio', this)">
            <span class="sb-icon">🏠</span> Inicio
        </div>
        <div class="sb-item" data-tab="tienda" onclick="irA('tienda', this)">
            <span class="sb-icon">🎨</span> Mi Tienda
        </div>
        <div class="sb-item" data-tab="ventas" onclick="irA('ventas', this)">
            <span class="sb-icon">📊</span> Ventas
            <?php if ($totalPendientes > 0): ?>
            <span class="badge badge-gold" style="margin-left:auto"><?= $totalPendientes ?></span>
            <?php endif; ?>
        </div>
        <div class="sb-item" data-tab="productos" onclick="irA('productos', this)">
            <span class="sb-icon">📦</span> Productos
            <span class="badge badge-gray" style="margin-left:auto"><?= $totalProductos ?></span>
        </div>
        <div class="sb-item" data-tab="agregar" onclick="irA('agregar', this)">
            <span class="sb-icon">➕</span> Agregar producto
        </div>
    </nav>

    <div class="sb-bottom">
        <a href="logout.php" class="btn-logout">
            <span>🚪</span> Cerrar sesión
        </a>
    </div>
</div>

<!-- ══════════════════════ MAIN ══════════════════════ -->
<div class="main">

<?php if ($flash): ?>
<div class="flash <?= $flash['tipo'] === 'ok' ? 'flash-ok' : 'flash-err' ?>">
    <?= $flash['tipo'] === 'ok' ? '✅' : '⚠️' ?>
    <?= htmlspecialchars($flash['msg']) ?>
</div>
<?php endif; ?>

<!-- ══════════ INICIO ══════════ -->
<div id="inicio" class="seccion activa">

    <div class="hero-card">
        <h1>Hola, <em><?= $nombre ?></em> 👋</h1>
        <p>Aquí tienes el resumen de tu tienda en Caboré. Todo listo para vender.</p>
        <div class="hero-btns" style="margin-top:20px">
            <button class="btn btn-gold" onclick="irA('ventas', document.querySelector('.sb-item[data-tab=ventas]'))">
                📊 Ver ventas
            </button>
            <button class="btn btn-outline" onclick="irA('tienda', document.querySelector('.sb-item[data-tab=tienda]'))">
                🎨 Configurar tienda
            </button>
            <button class="btn btn-outline" onclick="irA('agregar', document.querySelector('.sb-item[data-tab=agregar]'))">
                ➕ Nuevo producto
            </button>
            <a href="tienda.php?id=<?= $tienda_id ?>" target="_blank">
                <button class="btn btn-outline">🌐 Ver tienda pública</button>
            </a>
        </div>
    </div>

    <!-- Stats -->
    <div class="stats">
        <div class="stat-card">
            <div class="stat-label">Total productos</div>
            <div class="stat-value"><?= $totalProductos ?></div>
            <div class="stat-sub">en tu catálogo</div>
        </div>
        <div class="stat-card">
            <div class="stat-label">Productos activos</div>
            <div class="stat-value" style="color:#0c7a4d"><?= $totalActivos ?></div>
            <div class="stat-sub">visibles al público</div>
        </div>
        <div class="stat-card">
            <div class="stat-label">Destacados</div>
            <div class="stat-value" style="color:var(--gold)"><?= $totalDestacados ?></div>
            <div class="stat-sub">en sugerencias</div>
        </div>
        <div class="stat-card">
            <div class="stat-label">Valor inventario</div>
            <div class="stat-value" style="font-size:20px;margin-top:4px">
                $<?= number_format($valorInventario, 0, ',', '.') ?>
            </div>
            <div class="stat-sub">precio × stock</div>
        </div>
    </div>

    <!-- Vista previa de tienda -->
    <div class="store-card-preview">
        <div class="store-banner-preview">
            <?php if (!empty($tienda['banner'])): ?>
                <img src="<?= htmlspecialchars($tienda['banner']) ?>" alt="Banner">
            <?php else: ?>
                🏪
            <?php endif; ?>
        </div>
        <div class="store-card-inner">
            <?php if (!empty($tienda['logo'])): ?>
                <img src="<?= htmlspecialchars($tienda['logo']) ?>" class="store-logo-preview" alt="Logo">
            <?php else: ?>
                <div class="store-logo-preview">🏪</div>
            <?php endif; ?>
            <div class="store-info-preview">
                <h3><?= htmlspecialchars($tienda['nombre']) ?></h3>
                <p><?= htmlspecialchars($tienda['descripcion'] ?? '') ?></p>
                <div style="margin-top:8px;display:flex;align-items:center;gap:12px;flex-wrap:wrap">
                    <span>
                        <span class="color-swatch" style="background:<?= htmlspecialchars($tienda['color'] ?? '#c9a84c') ?>"></span>
                        Color activo
                    </span>
                    <?php if (!empty($tienda['ciudad'])): ?>
                        <span>📍 <?= htmlspecialchars($tienda['ciudad']) ?></span>
                    <?php endif; ?>
                    <span class="badge badge-green">● Activa</span>
                </div>
            </div>
            <a href="tienda.php?id=<?= $tienda_id ?>" target="_blank" style="margin-left:auto">
                <button class="btn btn-outline btn-sm">Ver pública 🌐</button>
            </a>
        </div>
    </div>

    <!-- Últimos productos -->
    <?php if ($totalProductos > 0): ?>
    <div class="panel-card">
        <div class="sec-header">
            <h2>Últimos productos</h2>
            <button class="btn btn-outline btn-sm" onclick="irA('productos', document.querySelector('.sb-item[data-tab=productos]'))">
                Ver todos →
            </button>
        </div>
        <table class="prod-table">
            <thead>
                <tr>
                    <th>Producto</th>
                    <th>Precio</th>
                    <th>Stock</th>
                    <th>Estado</th>
                </tr>
            </thead>
            <tbody>
            <?php foreach (array_slice($productos, 0, 5) as $p): ?>
                <tr>
                    <td>
                        <div style="display:flex;align-items:center;gap:12px">
                            <?php if (!empty($p['imagen']) && file_exists($p['imagen'])): ?>
                                <img src="<?= htmlspecialchars($p['imagen']) ?>" class="prod-thumb">
                            <?php else: ?>
                                <div class="prod-thumb"><?= $p['cat_icono'] ?? '📦' ?></div>
                            <?php endif; ?>
                            <div>
                                <div style="font-weight:600"><?= htmlspecialchars($p['nombre']) ?></div>
                                <div style="font-size:12px;color:var(--muted)"><?= $p['cat_nombre'] ?? 'Sin categoría' ?></div>
                            </div>
                        </div>
                    </td>
                    <td>
                        <span style="font-family:'Playfair Display',serif;font-weight:700;color:var(--gold)">
                            $<?= number_format($p['precio'], 0, ',', '.') ?>
                        </span>
                        <?php if (!empty($p['precio_oferta'])): ?>
                            <span class="oferta-tag">🏷️ Oferta: $<?= number_format($p['precio_oferta'], 0, ',', '.') ?></span>
                        <?php endif; ?>
                    </td>
                    <td>
                        <span style="font-weight:600"><?= $p['stock'] ?></span>
                        <div class="stock-bar">
                            <div class="stock-fill" style="width:<?= min(100, $p['stock'] * 5) ?>%"></div>
                        </div>
                    </td>
                    <td>
                        <?php if ($p['activo']): ?>
                            <span class="badge badge-green">Activo</span>
                        <?php else: ?>
                            <span class="badge badge-red">Inactivo</span>
                        <?php endif; ?>
                        <?php if ($p['destacado']): ?>
                            <span class="badge badge-gold" style="margin-left:4px">⭐ Dest.</span>
                        <?php endif; ?>
                    </td>
                </tr>
            <?php endforeach; ?>
            </tbody>
        </table>
    </div>
    <?php endif; ?>

</div><!-- /inicio -->

<!-- ══════════ VENTAS ══════════ -->
<div id="ventas" class="seccion">

    <div class="sec-header">
        <h2 style="font-family:'Playfair Display',serif;font-size:22px;font-weight:700">Ventas y estadísticas de compras</h2>
    </div>

    <!-- Stats de ventas -->
    <div class="stats">
        <div class="stat-card">
            <div class="stat-label">Ventas totales</div>
            <div class="stat-value" style="font-size:20px;margin-top:4px">$<?= number_format($totalVentas, 0, ',', '.') ?></div>
            <div class="stat-sub">histórico de tu tienda</div>
        </div>
        <div class="stat-card">
            <div class="stat-label">Pedidos recibidos</div>
            <div class="stat-value"><?= $totalPedidosVenta ?></div>
            <div class="stat-sub">con productos tuyos</div>
        </div>
        <div class="stat-card">
            <div class="stat-label">Ticket promedio</div>
            <div class="stat-value" style="font-size:20px;margin-top:4px">$<?= number_format($ticketPromedio, 0, ',', '.') ?></div>
            <div class="stat-sub">por pedido</div>
        </div>
        <div class="stat-card">
            <div class="stat-label">Pendientes</div>
            <div class="stat-value" style="color:<?= $totalPendientes > 0 ? '#b91c1c' : 'inherit' ?>"><?= $totalPendientes ?></div>
            <div class="stat-sub">por confirmar / enviar</div>
        </div>
    </div>

    <?php if (empty($ventas)): ?>

    <div class="panel-card">
        <div class="empty-state">
            <span>📊</span>
            <p>Todavía no tienes ventas registradas. Cuando alguien compre un producto tuyo, aparecerá aquí.</p>
        </div>
    </div>

    <?php else: ?>

    <!-- Mini gráfico últimos 7 días + Top productos -->
    <div style="display:grid;grid-template-columns:1.4fr 1fr;gap:20px;margin-bottom:24px">
        <div class="panel-card">
            <h2 style="font-size:16px;margin-bottom:18px">Ventas de los últimos 7 días</h2>
            <div style="display:flex;align-items:flex-end;gap:10px;height:140px">
                <?php foreach ($ultimos7dias as $d): ?>
                <div style="flex:1;display:flex;flex-direction:column;align-items:center;justify-content:flex-end;height:100%">
                    <div style="font-size:10px;color:var(--muted);margin-bottom:6px">
                        <?= $d['total'] > 0 ? '$' . number_format($d['total'], 0, ',', '.') : '' ?>
                    </div>
                    <div style="width:100%;max-width:34px;border-radius:6px 6px 0 0;background:linear-gradient(180deg,var(--gold),#a8832a);height:<?= max(4, round($d['total'] / $maxVentaDia * 100)) ?>%"></div>
                    <div style="font-size:11px;color:var(--muted);margin-top:8px;text-transform:capitalize"><?= $d['label'] ?></div>
                </div>
                <?php endforeach; ?>
            </div>
        </div>

        <div class="panel-card">
            <h2 style="font-size:16px;margin-bottom:16px">🏆 Más vendidos</h2>
            <?php foreach ($masVendidos as $mv): ?>
            <div style="display:flex;align-items:center;gap:12px;padding:9px 0;border-bottom:1px solid var(--border)">
                <?php if (!empty($mv['imagen']) && file_exists($mv['imagen'])): ?>
                    <img src="<?= htmlspecialchars($mv['imagen']) ?>" class="prod-thumb">
                <?php else: ?>
                    <div class="prod-thumb">📦</div>
                <?php endif; ?>
                <div style="flex:1;min-width:0">
                    <div style="font-weight:600;font-size:13px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis"><?= htmlspecialchars($mv['nombre']) ?></div>
                    <div style="font-size:12px;color:var(--muted)"><?= $mv['cantidad'] ?> vendidos</div>
                </div>
                <div style="font-weight:700;font-family:'Playfair Display',serif;color:var(--gold);font-size:13px;white-space:nowrap">
                    $<?= number_format($mv['total'], 0, ',', '.') ?>
                </div>
            </div>
            <?php endforeach; ?>
        </div>
    </div>

    <!-- Tabla de pedidos -->
    <div class="panel-card" style="padding:0;overflow:hidden">
        <div style="padding:20px 20px 0"><h2 style="font-size:16px">Registro de pedidos</h2></div>
        <table class="prod-table">
            <thead>
                <tr>
                    <th>Pedido</th>
                    <th>Comprador</th>
                    <th>Productos</th>
                    <th>Total</th>
                    <th>Estado</th>
                </tr>
            </thead>
            <tbody>
            <?php foreach ($pedidosAgrupados as $ped): ?>
                <tr>
                    <td>
                        <div style="font-weight:700">#<?= $ped['pedido_id'] ?></div>
                        <div style="font-size:11px;color:var(--muted)"><?= date('d/m/Y H:i', strtotime($ped['creado_en'])) ?></div>
                    </td>
                    <td>
                        <div style="font-weight:600"><?= htmlspecialchars($ped['comprador']) ?></div>
                        <?php if (!empty($ped['telefono'])): ?>
                        <div style="font-size:11px;color:var(--muted)">📞 <?= htmlspecialchars($ped['telefono']) ?></div>
                        <?php endif; ?>
                        <?php if (!empty($ped['direccion'])): ?>
                        <div style="font-size:11px;color:var(--muted)">📍 <?= htmlspecialchars($ped['direccion']) ?></div>
                        <?php endif; ?>
                    </td>
                    <td>
                        <?php foreach ($ped['items'] as $it): ?>
                        <div style="font-size:12.5px;margin-bottom:2px">
                            <?= $it['cantidad'] ?>× <?= htmlspecialchars($it['producto_nombre']) ?>
                        </div>
                        <?php endforeach; ?>
                    </td>
                    <td>
                        <span style="font-family:'Playfair Display',serif;font-weight:700;color:var(--gold)">
                            $<?= number_format($ped['total'], 0, ',', '.') ?>
                        </span>
                    </td>
                    <td>
                        <select class="estado-select" data-pedido="<?= $ped['pedido_id'] ?>" onchange="cambiarEstadoPedido(this)">
                            <?php foreach (['pendiente'=>'Pendiente','confirmado'=>'Confirmado','enviado'=>'En camino','entregado'=>'Entregado','cancelado'=>'Cancelado'] as $val => $label): ?>
                            <option value="<?= $val ?>" <?= $ped['estado'] === $val ? 'selected' : '' ?>><?= $label ?></option>
                            <?php endforeach; ?>
                        </select>
                    </td>
                </tr>
            <?php endforeach; ?>
            </tbody>
        </table>
    </div>

    <?php endif; ?>

</div><!-- /ventas -->

<!-- ══════════ TIENDA ══════════ -->
<div id="tienda" class="seccion">

    <div class="sec-header">
        <h2 style="font-family:'Playfair Display',serif;font-size:22px;font-weight:700">Configurar mi tienda</h2>
        <a href="tienda.php?id=<?= $tienda_id ?>" target="_blank">
            <button class="btn btn-outline btn-sm">🌐 Ver pública</button>
        </a>
    </div>

    <div class="tab-bar">
        <button class="tab-btn active" onclick="abrirTab('tab-info', this)">📋 Información</button>
        <button class="tab-btn" onclick="abrirTab('tab-apariencia', this)">🎨 Apariencia</button>
        <button class="tab-btn" onclick="abrirTab('tab-contacto', this)">📞 Contacto & Redes</button>
    </div>

    <!-- Tab: Información -->
    <div id="tab-info" class="tab-panel active">
        <div class="panel-card">
            <form action="actualizar_tienda.php" method="POST" enctype="multipart/form-data">
                <input type="hidden" name="seccion" value="info">
                <div class="form-grid">
                    <div class="form-group">
                        <label>Nombre de la tienda *</label>
                        <input type="text" name="nombre" value="<?= htmlspecialchars($tienda['nombre']) ?>" required placeholder="Ej: Boutique Luna">
                    </div>
                    <div class="form-group">
                        <label>Ciudad</label>
                        <input type="text" name="ciudad" value="<?= htmlspecialchars($tienda['ciudad'] ?? '') ?>" placeholder="Ej: Bogotá">
                    </div>
                    <div class="form-group full">
                        <label>Descripción de la tienda</label>
                        <textarea name="descripcion" placeholder="Cuéntale a tus clientes qué vendes y qué te hace especial..."><?= htmlspecialchars($tienda['descripcion'] ?? '') ?></textarea>
                    </div>
                </div>
                <div style="margin-top:20px">
                    <button type="submit" class="btn btn-gold">💾 Guardar información</button>
                </div>
            </form>
        </div>
    </div>

    <!-- Tab: Apariencia -->
    <div id="tab-apariencia" class="tab-panel">
        <div class="panel-card">
            <form action="actualizar_tienda.php" method="POST" enctype="multipart/form-data">
                <input type="hidden" name="seccion" value="apariencia">
                <div class="form-grid">
                    <div class="form-group">
                        <label>Color principal de tu tienda</label>
                        <div style="display:flex;gap:10px;align-items:center">
                            <input type="color" name="color" value="<?= htmlspecialchars($tienda['color'] ?? '#c9a84c') ?>" id="colorPicker" style="width:60px;flex-shrink:0">
                            <div style="font-size:13px;color:var(--muted)">Este color se aplica en los títulos, precios y botones de tu tienda pública.</div>
                        </div>
                        <!-- Colores rápidos -->
                        <div style="display:flex;gap:8px;margin-top:12px;flex-wrap:wrap">
                            <?php
                            $colores = ['#c9a84c','#3b82f6','#10b981','#f43f5e','#8b5cf6','#f97316','#06b6d4','#ec4899'];
                            foreach ($colores as $col):
                            ?>
                            <div onclick="document.getElementById('colorPicker').value='<?= $col ?>'"
                                 style="width:28px;height:28px;border-radius:50%;background:<?= $col ?>;cursor:pointer;border:2px solid rgba(28,24,16,0.18);transition:transform 0.2s"
                                 onmouseover="this.style.transform='scale(1.2)'"
                                 onmouseout="this.style.transform='scale(1)'">
                            </div>
                            <?php endforeach; ?>
                        </div>
                    </div>

                    <div class="form-group">
                        <label>Logo de la tienda</label>
                        <div style="display:flex;align-items:center;gap:14px">
                            <?php if (!empty($tienda['logo'])): ?>
                                <img src="<?= htmlspecialchars($tienda['logo']) ?>" style="width:56px;height:56px;border-radius:12px;object-fit:cover;border:1px solid var(--border)">
                            <?php else: ?>
                                <div style="width:56px;height:56px;border-radius:12px;background:var(--mid);display:flex;align-items:center;justify-content:center;font-size:24px;border:1px solid var(--border)">🏪</div>
                            <?php endif; ?>
                            <div style="flex:1">
                                <input type="file" name="logo" accept="image/*" style="font-size:12px">
                                <div style="font-size:11px;color:var(--muted);margin-top:4px">JPG, PNG, WEBP — máx 2MB</div>
                            </div>
                        </div>
                    </div>

                    <div class="form-group full">
                        <label>Banner de la tienda</label>
                        <?php if (!empty($tienda['banner'])): ?>
                            <img src="<?= htmlspecialchars($tienda['banner']) ?>" style="width:100%;height:100px;object-fit:cover;border-radius:10px;margin-bottom:10px;border:1px solid var(--border)">
                        <?php endif; ?>
                        <input type="file" name="banner" accept="image/*" style="font-size:12px">
                        <div style="font-size:11px;color:var(--muted);margin-top:4px">Imagen ancha que aparece en la cabecera de tu tienda pública. Recomendado: 1200×300px</div>
                    </div>
                </div>
                <div style="margin-top:20px">
                    <button type="submit" class="btn btn-gold">🎨 Guardar apariencia</button>
                </div>
            </form>
        </div>
    </div>

    <!-- Tab: Contacto & Redes -->
    <div id="tab-contacto" class="tab-panel">
        <div class="panel-card">
            <form action="actualizar_tienda.php" method="POST">
                <input type="hidden" name="seccion" value="contacto">
                <div class="form-grid">
                    <div class="form-group">
                        <label>WhatsApp</label>
                        <input type="text" name="whatsapp" value="<?= htmlspecialchars($tienda['whatsapp'] ?? '') ?>" placeholder="Ej: 3001234567">
                        <div style="font-size:11px;color:var(--muted);margin-top:5px">Solo el número, sin espacios ni +57</div>
                    </div>
                    <div class="form-group">
                        <label>Instagram</label>
                        <input type="text" name="instagram" value="<?= htmlspecialchars($tienda['instagram'] ?? '') ?>" placeholder="Ej: @mitienda">
                    </div>
                </div>

                <?php if (!empty($tienda['whatsapp']) || !empty($tienda['instagram'])): ?>
                <div class="social-links" style="margin-top:16px;margin-bottom:16px">
                    <?php if (!empty($tienda['whatsapp'])): ?>
                        <a href="https://wa.me/57<?= htmlspecialchars($tienda['whatsapp']) ?>" target="_blank" class="social-chip">📱 WhatsApp</a>
                    <?php endif; ?>
                    <?php if (!empty($tienda['instagram'])): ?>
                        <a href="https://instagram.com/<?= ltrim(htmlspecialchars($tienda['instagram']), '@') ?>" target="_blank" class="social-chip">📸 Instagram</a>
                    <?php endif; ?>
                </div>
                <?php endif; ?>

                <button type="submit" class="btn btn-gold">📞 Guardar contacto</button>
            </form>
        </div>
    </div>

</div><!-- /tienda -->

<!-- ══════════ PRODUCTOS ══════════ -->
<div id="productos" class="seccion">

    <div class="sec-header">
        <h2 style="font-family:'Playfair Display',serif;font-size:22px;font-weight:700">Mis productos</h2>
        <div style="display:flex;gap:10px;align-items:center">
            <div class="view-toggle">
                <button class="view-btn active" id="btn-grilla" onclick="toggleVista('grilla')">⊞</button>
                <button class="view-btn" id="btn-tabla" onclick="toggleVista('tabla')">≡</button>
            </div>
            <button class="btn btn-gold btn-sm" onclick="irA('agregar', document.querySelector('.sb-item[data-tab=agregar]'))">
                ➕ Agregar
            </button>
        </div>
    </div>

    <?php if ($totalProductos === 0): ?>
        <div class="panel-card">
            <div class="empty-state">
                <span>📦</span>
                <p>Aún no tienes productos. ¡Agrega el primero!</p>
                <button class="btn btn-gold" style="margin-top:16px" onclick="irA('agregar', document.querySelector('.sb-item[data-tab=agregar]'))">
                    ➕ Agregar producto
                </button>
            </div>
        </div>
    <?php else: ?>

        <!-- VISTA GRILLA -->
        <div id="vista-grilla">
            <div class="product-grid">
            <?php foreach ($productos as $p): ?>
                <div class="product-card">
                    <?php if (!empty($p['imagen']) && file_exists($p['imagen'])): ?>
                        <img class="product-img" src="<?= htmlspecialchars($p['imagen']) ?>" alt="<?= htmlspecialchars($p['nombre']) ?>">
                    <?php else: ?>
                        <div class="product-img-placeholder"><?= $p['cat_icono'] ?? '📷' ?></div>
                    <?php endif; ?>
                    <div class="product-body">
                        <div style="font-size:11px;color:var(--muted);margin-bottom:4px"><?= $p['cat_nombre'] ?? '' ?></div>
                        <div class="product-name"><?= htmlspecialchars($p['nombre']) ?></div>
                        <div class="product-price">$<?= number_format($p['precio'], 0, ',', '.') ?></div>
                        <?php if (!empty($p['precio_oferta'])): ?>
                            <div class="oferta-tag">🏷️ $<?= number_format($p['precio_oferta'], 0, ',', '.') ?></div>
                        <?php endif; ?>
                        <div style="margin-top:6px;display:flex;gap:6px;flex-wrap:wrap">
                            <?= $p['activo'] ? '<span class="badge badge-green">Activo</span>' : '<span class="badge badge-red">Inactivo</span>' ?>
                            <?php if ($p['destacado']): ?><span class="badge badge-gold">⭐</span><?php endif; ?>
                            <span class="badge badge-gray">Stock: <?= $p['stock'] ?></span>
                        </div>
                        <div class="product-actions" style="margin-top:12px">
                            <a href="editar_producto.php?id=<?= $p['id'] ?>">
                                <button class="btn btn-outline btn-sm">✏️ Editar</button>
                            </a>
                            <form action="eliminar_producto.php" method="POST" onsubmit="return confirm('¿Eliminar «<?= htmlspecialchars(addslashes($p['nombre'])) ?>»?')">
                                <input type="hidden" name="id" value="<?= $p['id'] ?>">
                                <button type="submit" class="btn btn-red btn-sm">🗑️</button>
                            </form>
                        </div>
                    </div>
                </div>
            <?php endforeach; ?>
            </div>
        </div>

        <!-- VISTA TABLA -->
        <div id="vista-tabla" style="display:none">
            <div class="panel-card" style="padding:0;overflow:hidden">
                <table class="prod-table">
                    <thead>
                        <tr>
                            <th>Producto</th>
                            <th>Precio</th>
                            <th>Stock</th>
                            <th>Estado</th>
                            <th>Acciones</th>
                        </tr>
                    </thead>
                    <tbody>
                    <?php foreach ($productos as $p): ?>
                        <tr>
                            <td>
                                <div style="display:flex;align-items:center;gap:12px">
                                    <?php if (!empty($p['imagen']) && file_exists($p['imagen'])): ?>
                                        <img src="<?= htmlspecialchars($p['imagen']) ?>" class="prod-thumb">
                                    <?php else: ?>
                                        <div class="prod-thumb"><?= $p['cat_icono'] ?? '📦' ?></div>
                                    <?php endif; ?>
                                    <div>
                                        <div style="font-weight:600"><?= htmlspecialchars($p['nombre']) ?></div>
                                        <div style="font-size:12px;color:var(--muted)"><?= $p['cat_nombre'] ?? 'Sin categoría' ?></div>
                                    </div>
                                </div>
                            </td>
                            <td>
                                <span style="font-family:'Playfair Display',serif;font-weight:700;color:var(--gold)">
                                    $<?= number_format($p['precio'], 0, ',', '.') ?>
                                </span>
                                <?php if (!empty($p['precio_oferta'])): ?>
                                    <span class="oferta-tag">🏷️ $<?= number_format($p['precio_oferta'], 0, ',', '.') ?></span>
                                <?php endif; ?>
                            </td>
                            <td>
                                <span style="font-weight:600"><?= $p['stock'] ?> uds</span>
                                <div class="stock-bar">
                                    <div class="stock-fill" style="width:<?= min(100, $p['stock'] * 5) ?>%"></div>
                                </div>
                            </td>
                            <td>
                                <?= $p['activo'] ? '<span class="badge badge-green">Activo</span>' : '<span class="badge badge-red">Inactivo</span>' ?>
                                <?php if ($p['destacado']): ?>
                                    <span class="badge badge-gold" style="margin-left:4px">⭐</span>
                                <?php endif; ?>
                            </td>
                            <td>
                                <div style="display:flex;gap:6px">
                                    <a href="editar_producto.php?id=<?= $p['id'] ?>">
                                        <button class="btn btn-outline btn-sm">✏️</button>
                                    </a>
                                    <form action="eliminar_producto.php" method="POST" onsubmit="return confirm('¿Eliminar este producto?')">
                                        <input type="hidden" name="id" value="<?= $p['id'] ?>">
                                        <button type="submit" class="btn btn-red btn-sm">🗑️</button>
                                    </form>
                                </div>
                            </td>
                        </tr>
                    <?php endforeach; ?>
                    </tbody>
                </table>
            </div>
        </div>

    <?php endif; ?>
</div><!-- /productos -->

<!-- ══════════ AGREGAR PRODUCTO ══════════ -->
<div id="agregar" class="seccion">
    <div class="panel-card">
        <h2>➕ Agregar producto</h2>

        <form action="guardar_producto.php" method="POST" enctype="multipart/form-data">
            <div class="form-grid">

                <div class="form-group">
                    <label>Nombre del producto *</label>
                    <input type="text" name="nombre" placeholder="Ej: Camiseta deportiva azul" required>
                </div>

                <div class="form-group">
                    <label>Categoría</label>
                    <select name="categoria_id">
                        <?php $categorias->data_seek(0); while ($c = $categorias->fetch_assoc()): ?>
                            <option value="<?= $c['id'] ?>"><?= $c['icono'] ?> <?= htmlspecialchars($c['nombre']) ?></option>
                        <?php endwhile; ?>
                    </select>
                </div>

                <div class="form-group">
                    <label>Precio (COP) *</label>
                    <input type="number" name="precio" placeholder="Ej: 50000" min="0" step="100" required>
                </div>

                <div class="form-group">
                    <label>Precio en oferta (COP) <span style="color:var(--muted);font-weight:400">(opcional)</span></label>
                    <input type="number" name="precio_oferta" placeholder="Ej: 39900" min="0" step="100">
                </div>

                <div class="form-group">
                    <label>Stock disponible</label>
                    <input type="number" name="stock" placeholder="Ej: 20" min="0" value="0">
                </div>

                <div class="form-group" style="display:flex;flex-direction:column;justify-content:flex-end;gap:12px">
                    <label style="display:flex;align-items:center;gap:10px;cursor:pointer;text-transform:none;font-size:14px;font-weight:500">
                        <input type="checkbox" name="destacado" value="1" style="width:16px;height:16px">
                        ⭐ Marcar como producto destacado
                    </label>
                    <label style="display:flex;align-items:center;gap:10px;cursor:pointer;text-transform:none;font-size:14px;font-weight:500">
                        <input type="checkbox" name="activo" value="1" checked style="width:16px;height:16px">
                        ✅ Producto activo (visible al público)
                    </label>
                </div>

                <div class="form-group full">
                    <label>Descripción del producto</label>
                    <textarea name="descripcion" placeholder="Describe materiales, tallas, colores disponibles, características especiales..."></textarea>
                </div>

                <div class="form-group full">
                    <label>Imagen del producto</label>
                    <input type="file" name="imagen" accept="image/*" id="imgInput" onchange="previewImg(this)">
                    <div id="imgPreview" style="margin-top:12px;display:none">
                        <img id="imgPreviewSrc" style="max-height:180px;border-radius:10px;border:1px solid var(--border)">
                    </div>
                    <div style="font-size:11px;color:var(--muted);margin-top:6px">JPG, PNG, WEBP — máx 5MB. Recomendado: imagen cuadrada 600×600px</div>
                </div>

            </div>

            <div style="margin-top:24px;display:flex;gap:12px">
                <button type="submit" class="btn btn-gold">💾 Guardar producto</button>
                <button type="reset" class="btn btn-outline">🔄 Limpiar</button>
            </div>
        </form>
    </div>
</div><!-- /agregar -->

</div><!-- /main -->

<script>
// Navegación entre secciones
function irA(id, elNav) {
    document.querySelectorAll('.seccion').forEach(s => s.classList.remove('activa'));
    document.getElementById(id).classList.add('activa');
    document.querySelectorAll('.sb-item').forEach(i => i.classList.remove('active'));
    if (elNav && elNav.classList) elNav.classList.add('active');
}

// Tabs dentro de tienda
function abrirTab(id, btn) {
    document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    document.getElementById(id).classList.add('active');
    btn.classList.add('active');
}

// Vista grilla / tabla
function toggleVista(modo) {
    const grilla = document.getElementById('vista-grilla');
    const tabla  = document.getElementById('vista-tabla');
    const btnG   = document.getElementById('btn-grilla');
    const btnT   = document.getElementById('btn-tabla');
    if (modo === 'grilla') {
        grilla.style.display = 'block';
        tabla.style.display  = 'none';
        btnG.classList.add('active');
        btnT.classList.remove('active');
    } else {
        grilla.style.display = 'none';
        tabla.style.display  = 'block';
        btnT.classList.add('active');
        btnG.classList.remove('active');
    }
}

// Cambiar estado de un pedido (pestaña Ventas)
function cambiarEstadoPedido(select) {
    const pedidoId = select.dataset.pedido;
    const estado   = select.value;
    const original = select.dataset.original || estado;
    select.disabled = true;

    fetch('actualizar_pedido.php', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `pedido_id=${encodeURIComponent(pedidoId)}&estado=${encodeURIComponent(estado)}`
    })
    .then(r => r.json())
    .then(data => {
        select.disabled = false;
        if (data.ok) {
            select.dataset.original = estado;
        } else {
            select.value = original;
            alert(data.error || 'No se pudo actualizar el estado del pedido.');
        }
    })
    .catch(() => {
        select.disabled = false;
        select.value = original;
        alert('Error de conexión al actualizar el pedido.');
    });
}

// Preview imagen
function previewImg(input) {
    if (input.files && input.files[0]) {
        const reader = new FileReader();
        reader.onload = e => {
            document.getElementById('imgPreviewSrc').src = e.target.result;
            document.getElementById('imgPreview').style.display = 'block';
        };
        reader.readAsDataURL(input.files[0]);
    }
}

// Auto-hide flash
const flash = document.querySelector('.flash');
if (flash) setTimeout(() => flash.style.opacity = '0', 4000);
</script>
</body>
</html>