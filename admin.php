<?php
session_start();
include("conexion.php");

// Protección de acceso — solo administradores
if (!isset($_SESSION['usuario_id']) || $_SESSION['rol'] !== 'administrador') {
    header("Location: login.html"); exit();
}

$admin_id = (int) $_SESSION['usuario_id'];

$stmtU = $conn->prepare("SELECT nombre FROM usuarios WHERE id = ?");
$stmtU->bind_param("i", $admin_id);
$stmtU->execute();
$admin  = $stmtU->get_result()->fetch_assoc();
$nombre = htmlspecialchars($admin['nombre'] ?? 'Admin');

$estados_validos = ['pendiente', 'confirmado', 'enviado', 'entregado', 'cancelado'];

/* ══════════════════════ KPIs GENERALES ══════════════════════ */
$totalUsuarios = (int) $conn->query("SELECT COUNT(*) n FROM usuarios")->fetch_assoc()['n'];
$totalTiendas  = (int) $conn->query("SELECT COUNT(*) n FROM tiendas")->fetch_assoc()['n'];

$porRol = ['cliente' => 0, 'emprendedor' => 0, 'administrador' => 0];
$resRol = $conn->query("SELECT rol, COUNT(*) n FROM usuarios GROUP BY rol");
while ($r = $resRol->fetch_assoc()) { $porRol[$r['rol']] = (int) $r['n']; }

$porEstadoVerif = ['pendiente' => 0, 'aprobado' => 0, 'rechazado' => 0];
$resVerif = $conn->query("SELECT estado_verificacion, COUNT(*) n FROM productos GROUP BY estado_verificacion");
while ($r = $resVerif->fetch_assoc()) { $porEstadoVerif[$r['estado_verificacion']] = (int) $r['n']; }
$totalProductos = array_sum($porEstadoVerif);

$resPed = $conn->query("SELECT COUNT(*) n, COALESCE(SUM(total),0) total FROM pedidos WHERE estado != 'cancelado'")->fetch_assoc();
$totalPedidos = (int) $resPed['n'];
$totalVentas  = (float) $resPed['total'];
$pedidosPend  = (int) $conn->query("SELECT COUNT(*) n FROM pedidos WHERE estado = 'pendiente'")->fetch_assoc()['n'];

/* Productos pendientes recientes (para el resumen) */
$pendientesPreview = $conn->query("
    SELECT p.id, p.nombre, p.imagen, p.precio, p.creado_en, t.nombre AS tienda_nombre
    FROM productos p JOIN tiendas t ON t.id = p.tienda_id
    WHERE p.estado_verificacion = 'pendiente'
    ORDER BY p.id DESC LIMIT 5
")->fetch_all(MYSQLI_ASSOC);

/* Últimos pedidos (para el resumen) */
$pedidosPreview = $conn->query("
    SELECT p.id, p.total, p.estado, p.creado_en, u.nombre AS cliente_nombre
    FROM pedidos p JOIN usuarios u ON u.id = p.usuario_id
    ORDER BY p.creado_en DESC LIMIT 6
")->fetch_all(MYSQLI_ASSOC);

/* ══════════════════════ VERIFICACIÓN DE PRODUCTOS ══════════════════════ */
$vtab = $_GET['vtab'] ?? 'pendiente';
if (!in_array($vtab, ['pendiente', 'aprobado', 'rechazado'], true)) { $vtab = 'pendiente'; }

$stmtProd = $conn->prepare("
    SELECT p.id, p.nombre, p.descripcion, p.precio, p.stock, p.imagen, p.creado_en,
           p.motivo_rechazo, p.revisado_en,
           t.id AS tienda_id, t.nombre AS tienda_nombre,
           u.nombre AS emprendedor_nombre, u.correo AS emprendedor_correo, u.telefono AS emprendedor_telefono,
           c.nombre AS cat_nombre
    FROM productos p
    JOIN tiendas t   ON t.id = p.tienda_id
    JOIN usuarios u  ON u.id = t.usuario_id
    LEFT JOIN categorias c ON c.id = p.categoria_id
    WHERE p.estado_verificacion = ?
    ORDER BY p.id DESC
    LIMIT 150
");
$stmtProd->bind_param("s", $vtab);
$stmtProd->execute();
$productosVerif = $stmtProd->get_result()->fetch_all(MYSQLI_ASSOC);

/* ══════════════════════ REPORTES ══════════════════════ */
$desde    = $_GET['desde']  ?? date('Y-m-d', strtotime('-30 days'));
$hasta    = $_GET['hasta']  ?? date('Y-m-d');
$estado_f = $_GET['estado'] ?? '';
$tienda_f = (int) ($_GET['tienda_id'] ?? 0);

$tiendasList = $conn->query("SELECT id, nombre FROM tiendas ORDER BY nombre")->fetch_all(MYSQLI_ASSOC);

$topTiendas   = [];
$topProductos = [];

if ($tienda_f > 0) {
    // ── Reporte de UNA tienda específica (a nivel de ítems vendidos) ──
    $where  = ["pr.tienda_id = ?", "p.creado_en >= ?", "p.creado_en <= ?"];
    $params = [$tienda_f, "$desde 00:00:00", "$hasta 23:59:59"];
    $types  = "iss";
    if ($estado_f !== '' && in_array($estado_f, $estados_validos, true)) {
        $where[] = "p.estado = ?"; $params[] = $estado_f; $types .= "s";
    }
    $sql = "SELECT pi.cantidad, pi.precio_unitario, pi.producto_id, p.id AS pedido_id,
                   pr.nombre AS producto_nombre
            FROM pedido_items pi
            JOIN pedidos p   ON p.id = pi.pedido_id
            JOIN productos pr ON pr.id = pi.producto_id
            WHERE " . implode(' AND ', $where);
    $stmt = $conn->prepare($sql);
    $stmt->bind_param($types, ...$params);
    $stmt->execute();
    $filas = $stmt->get_result()->fetch_all(MYSQLI_ASSOC);

    $totalVentasP = 0; $pedidosSet = [];
    foreach ($filas as $f) {
        $sub = $f['cantidad'] * $f['precio_unitario'];
        $totalVentasP += $sub;
        $pedidosSet[$f['pedido_id']] = true;
        $pid = $f['producto_id'];
        if (!isset($topProductos[$pid])) $topProductos[$pid] = ['nombre' => $f['producto_nombre'], 'cantidad' => 0, 'total' => 0];
        $topProductos[$pid]['cantidad'] += $f['cantidad'];
        $topProductos[$pid]['total']    += $sub;
    }
    $totalPedidosP = count($pedidosSet);
} else {
    // ── Reporte de TODO el marketplace (a nivel de pedidos) ──
    $where  = ["p.creado_en >= ?", "p.creado_en <= ?"];
    $params = ["$desde 00:00:00", "$hasta 23:59:59"];
    $types  = "ss";
    if ($estado_f !== '' && in_array($estado_f, $estados_validos, true)) {
        $where[] = "p.estado = ?"; $params[] = $estado_f; $types .= "s";
    }
    $sqlResumen = "SELECT COUNT(*) n, COALESCE(SUM(p.total),0) total FROM pedidos p WHERE " . implode(' AND ', $where);
    $stmt = $conn->prepare($sqlResumen);
    $stmt->bind_param($types, ...$params);
    $stmt->execute();
    $resumen = $stmt->get_result()->fetch_assoc();
    $totalPedidosP = (int) $resumen['n'];
    $totalVentasP  = (float) $resumen['total'];

    // Top tiendas del período
    $sqlTop = "SELECT t.id, t.nombre, SUM(pi.cantidad*pi.precio_unitario) AS total, COUNT(DISTINCT p.id) AS pedidos
               FROM pedido_items pi
               JOIN pedidos p     ON p.id = pi.pedido_id
               JOIN productos pr  ON pr.id = pi.producto_id
               JOIN tiendas t     ON t.id = pr.tienda_id
               WHERE " . implode(' AND ', $where) . "
               GROUP BY t.id ORDER BY total DESC LIMIT 5";
    $stmt = $conn->prepare($sqlTop);
    $stmt->bind_param($types, ...$params);
    $stmt->execute();
    $topTiendas = $stmt->get_result()->fetch_all(MYSQLI_ASSOC);

    // Top productos del período (todas las tiendas)
    $sqlTopP = "SELECT pr.id, pr.nombre, SUM(pi.cantidad) AS cantidad, SUM(pi.cantidad*pi.precio_unitario) AS total
                FROM pedido_items pi
                JOIN pedidos p    ON p.id = pi.pedido_id
                JOIN productos pr ON pr.id = pi.producto_id
                WHERE " . implode(' AND ', $where) . "
                GROUP BY pr.id ORDER BY total DESC LIMIT 5";
    $stmt = $conn->prepare($sqlTopP);
    $stmt->bind_param($types, ...$params);
    $stmt->execute();
    $tp = $stmt->get_result()->fetch_all(MYSQLI_ASSOC);
    foreach ($tp as $row) { $topProductos[$row['id']] = $row; }
}
usort($topProductos, fn($a, $b) => $b['total'] <=> $a['total']);
$ticketProm = $totalPedidosP > 0 ? $totalVentasP / $totalPedidosP : 0;

// Ventas de los últimos 14 días (todo el marketplace, para el mini gráfico)
$ventasDia = [];
$resDias = $conn->query("
    SELECT DATE(creado_en) AS dia, SUM(total) AS total
    FROM pedidos
    WHERE creado_en >= DATE_SUB(NOW(), INTERVAL 14 DAY) AND estado != 'cancelado'
    GROUP BY DATE(creado_en)
");
while ($r = $resDias->fetch_assoc()) { $ventasDia[$r['dia']] = (float) $r['total']; }
$ultimos14 = [];
for ($i = 13; $i >= 0; $i--) {
    $dia = date('Y-m-d', strtotime("-$i days"));
    $ultimos14[] = ['fecha' => $dia, 'label' => date('d/m', strtotime($dia)), 'total' => $ventasDia[$dia] ?? 0];
}
$maxVentaDia = max(array_column($ultimos14, 'total')) ?: 1;

/* ══════════════════════ FACTURAS ══════════════════════ */
$busq_id      = trim($_GET['f_id'] ?? '');
$busq_cliente = trim($_GET['f_cliente'] ?? '');

$whereF  = ["1=1"];
$paramsF = [];
$typesF  = "";
if ($busq_id !== '') {
    $whereF[]  = "p.id = ?";
    $paramsF[] = (int) preg_replace('/\D/', '', $busq_id);
    $typesF   .= "i";
}
if ($busq_cliente !== '') {
    $whereF[]  = "u.nombre LIKE ?";
    $paramsF[] = "%$busq_cliente%";
    $typesF   .= "s";
}
$sqlF = "
    SELECT p.id, p.total, p.estado, p.creado_en, u.nombre AS cliente_nombre
    FROM pedidos p JOIN usuarios u ON u.id = p.usuario_id
    WHERE " . implode(' AND ', $whereF) . "
    ORDER BY p.creado_en DESC
    LIMIT 50
";
$stmtF = $conn->prepare($sqlF);
if ($paramsF) { $stmtF->bind_param($typesF, ...$paramsF); }
$stmtF->execute();
$facturasList = $stmtF->get_result()->fetch_all(MYSQLI_ASSOC);

/* ══════════════════════ USUARIOS ══════════════════════ */
$rol_f    = $_GET['rol'] ?? '';
$buscar_u = trim($_GET['u_buscar'] ?? '');

$whereU  = ["1=1"];
$paramsU = [];
$typesU  = "";
if ($rol_f !== '' && in_array($rol_f, ['cliente','emprendedor','administrador'], true)) {
    $whereU[] = "rol = ?"; $paramsU[] = $rol_f; $typesU .= "s";
}
if ($buscar_u !== '') {
    $whereU[]  = "(nombre LIKE ? OR correo LIKE ?)";
    $paramsU[] = "%$buscar_u%"; $paramsU[] = "%$buscar_u%";
    $typesU   .= "ss";
}
$sqlU = "SELECT id, nombre, correo, rol, activo FROM usuarios WHERE " . implode(' AND ', $whereU) . " ORDER BY id DESC LIMIT 200";
$stmtUL = $conn->prepare($sqlU);
if ($paramsU) { $stmtUL->bind_param($typesU, ...$paramsU); }
$stmtUL->execute();
$usuariosList = $stmtUL->get_result()->fetch_all(MYSQLI_ASSOC);

/* ══════════════════════ CATEGORÍAS ══════════════════════ */
$categoriasList = $conn->query("
    SELECT c.id, c.nombre, c.icono, COUNT(p.id) AS total_productos
    FROM categorias c
    LEFT JOIN productos p ON p.categoria_id = c.id
    GROUP BY c.id
    ORDER BY c.nombre
")->fetch_all(MYSQLI_ASSOC);

/* ══════════════════════ TIENDAS ══════════════════════ */
$buscar_t = trim($_GET['t_buscar'] ?? '');
$whereT   = ["1=1"];
$paramsT  = [];
$typesT   = "";
if ($buscar_t !== '') {
    $whereT[]  = "(t.nombre LIKE ? OR u.nombre LIKE ? OR u.correo LIKE ?)";
    $paramsT[] = "%$buscar_t%"; $paramsT[] = "%$buscar_t%"; $paramsT[] = "%$buscar_t%";
    $typesT   .= "sss";
}
$sqlT = "
    SELECT t.id, t.nombre, t.activo, t.motivo_suspension,
           u.nombre AS dueno_nombre, u.correo AS dueno_correo,
           COUNT(DISTINCT p.id) AS total_productos,
           COALESCE(SUM(pi.cantidad * pi.precio_unitario), 0) AS total_ventas
    FROM tiendas t
    JOIN usuarios u ON u.id = t.usuario_id
    LEFT JOIN productos p ON p.tienda_id = t.id
    LEFT JOIN pedido_items pi ON pi.producto_id = p.id
    WHERE " . implode(' AND ', $whereT) . "
    GROUP BY t.id
    ORDER BY t.id DESC
";
$stmtTL = $conn->prepare($sqlT);
if ($paramsT) { $stmtTL->bind_param($typesT, ...$paramsT); }
$stmtTL->execute();
$tiendasGestion = $stmtTL->get_result()->fetch_all(MYSQLI_ASSOC);

$flash = $_SESSION['flash'] ?? null;
unset($_SESSION['flash']);
?>
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Caboré — Administrador</title>
<link href="https://fonts.googleapis.com/css2?family=Playfair+Display:wght@700;900&family=DM+Sans:wght@300;400;500;600&display=swap" rel="stylesheet">
<link rel="stylesheet" href="css/emprendedor.css">
<link rel="stylesheet" href="css/admin.css">
</head>
<body>
<div class="bg-grid"></div>

<!-- ══════════════════════ SIDEBAR ══════════════════════ -->
<div class="sidebar">
    <div class="sb-logo">Cabo<span>ré</span></div>

    <div class="sb-user">
        <div class="sb-avatar">🛡️</div>
        <div class="sb-user-name"><?= $nombre ?></div>
        <div class="sb-user-role">Administrador</div>
    </div>

    <nav class="sb-nav">
        <div class="sb-item active" data-tab="resumen" onclick="irA('resumen', this)">
            <span class="sb-icon">🏠</span> Resumen
        </div>
        <div class="sb-item" data-tab="verificacion" onclick="irA('verificacion', this)">
            <span class="sb-icon">🔍</span> Verificación
            <?php if ($porEstadoVerif['pendiente'] > 0): ?>
            <span class="badge badge-gold" style="margin-left:auto"><?= $porEstadoVerif['pendiente'] ?></span>
            <?php endif; ?>
        </div>
        <div class="sb-item" data-tab="reportes" onclick="irA('reportes', this)">
            <span class="sb-icon">📊</span> Reportes
        </div>
        <div class="sb-item" data-tab="facturas" onclick="irA('facturas', this)">
            <span class="sb-icon">🧾</span> Facturas
        </div>
        <div class="sb-item" data-tab="usuarios" onclick="irA('usuarios', this)">
            <span class="sb-icon">👥</span> Usuarios
        </div>
        <div class="sb-item" data-tab="categorias" onclick="irA('categorias', this)">
            <span class="sb-icon">🏷️</span> Categorías
        </div>
        <div class="sb-item" data-tab="tiendas" onclick="irA('tiendas', this)">
            <span class="sb-icon">🏪</span> Tiendas
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

<!-- ══════════ RESUMEN ══════════ -->
<div id="resumen" class="seccion activa">

    <div class="hero-card">
        <h1>Administrador</h1>
        <p>Gestiona la confiabilidad del marketplace y genera reportes de ventas y facturas.</p>
    </div>

    <div class="stats">
        <div class="stat-card">
            <div class="stat-label">Usuarios</div>
            <div class="stat-value"><?= $totalUsuarios ?></div>
            <div class="stat-sub"><?= $porRol['cliente'] ?> clientes · <?= $porRol['emprendedor'] ?> emprendedores</div>
        </div>
        <div class="stat-card">
            <div class="stat-label">Tiendas</div>
            <div class="stat-value"><?= $totalTiendas ?></div>
            <div class="stat-sub">activas en la plataforma</div>
        </div>
        <div class="stat-card">
            <div class="stat-label">Productos por verificar</div>
            <div class="stat-value" style="color:<?= $porEstadoVerif['pendiente'] > 0 ? '#b91c1c' : 'inherit' ?>"><?= $porEstadoVerif['pendiente'] ?></div>
            <div class="stat-sub">de <?= $totalProductos ?> productos totales</div>
        </div>
        <div class="stat-card">
            <div class="stat-label">Ventas totales</div>
            <div class="stat-value" style="font-size:20px;margin-top:4px">$<?= number_format($totalVentas, 0, ',', '.') ?></div>
            <div class="stat-sub"><?= $totalPedidos ?> pedidos · <?= $pedidosPend ?> pendientes</div>
        </div>
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:20px">
        <!-- Productos pendientes -->
        <div class="panel-card">
            <div class="sec-header">
                <h2 style="font-size:16px">🔍 Productos por verificar</h2>
                <button class="btn btn-outline btn-sm" onclick="irA('verificacion', document.querySelector('.sb-item[data-tab=verificacion]'))">Ver todos</button>
            </div>
            <?php if (empty($pendientesPreview)): ?>
                <div class="empty-state" style="padding:24px"><span>✅</span><p>No hay productos pendientes de revisión.</p></div>
            <?php else: foreach ($pendientesPreview as $p): ?>
                <div style="display:flex;align-items:center;gap:12px;padding:9px 0;border-bottom:1px solid var(--border)">
                    <?php if (!empty($p['imagen']) && file_exists($p['imagen'])): ?>
                        <img src="<?= htmlspecialchars($p['imagen']) ?>" class="prod-thumb">
                    <?php else: ?>
                        <div class="prod-thumb">📦</div>
                    <?php endif; ?>
                    <div style="flex:1;min-width:0">
                        <div style="font-weight:600;font-size:13px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis"><?= htmlspecialchars($p['nombre']) ?></div>
                        <div style="font-size:12px;color:var(--muted)"><?= htmlspecialchars($p['tienda_nombre']) ?></div>
                    </div>
                    <div style="font-weight:700;font-family:'Playfair Display',serif;color:var(--gold);font-size:13px;white-space:nowrap">
                        $<?= number_format($p['precio'], 0, ',', '.') ?>
                    </div>
                </div>
            <?php endforeach; endif; ?>
        </div>

        <!-- Últimos pedidos -->
        <div class="panel-card">
            <div class="sec-header">
                <h2 style="font-size:16px">🧾 Últimos pedidos</h2>
                <button class="btn btn-outline btn-sm" onclick="irA('facturas', document.querySelector('.sb-item[data-tab=facturas]'))">Ver facturas</button>
            </div>
            <?php if (empty($pedidosPreview)): ?>
                <div class="empty-state" style="padding:24px"><span>🧾</span><p>Todavía no hay pedidos registrados.</p></div>
            <?php else: foreach ($pedidosPreview as $p): ?>
                <div style="display:flex;align-items:center;gap:12px;padding:9px 0;border-bottom:1px solid var(--border)">
                    <div style="flex:1;min-width:0">
                        <div style="font-weight:600;font-size:13px">#<?= $p['id'] ?> — <?= htmlspecialchars($p['cliente_nombre']) ?></div>
                        <div style="font-size:12px;color:var(--muted)"><?= date('d/m/Y H:i', strtotime($p['creado_en'])) ?></div>
                    </div>
                    <div style="font-weight:700;font-family:'Playfair Display',serif;color:var(--gold);font-size:13px">
                        $<?= number_format($p['total'], 0, ',', '.') ?>
                    </div>
                </div>
            <?php endforeach; endif; ?>
        </div>
    </div>

</div><!-- /resumen -->

<!-- ══════════ VERIFICACIÓN ══════════ -->
<div id="verificacion" class="seccion">
    <div class="sec-header">
        <h2>🔍 Verificación de productos</h2>
    </div>
    <p style="color:var(--muted);font-size:13.5px;margin-bottom:18px;max-width:680px">
        Revisa cada producto nuevo subido por los emprendedores antes de que aparezca visible para los clientes.
        Aprueba lo que cumpla con las políticas de la plataforma, o recházalo indicando el motivo (el emprendedor
        podrá corregirlo y volver a intentarlo).
    </p>

    <div class="vtabs">
        <a href="?tab=verificacion&vtab=pendiente" class="vtab-btn <?= $vtab === 'pendiente' ? 'active' : '' ?>" onclick="return irVtab(event,'pendiente')">
            ⏳ Pendientes <span class="badge badge-gold"><?= $porEstadoVerif['pendiente'] ?></span>
        </a>
        <a href="?tab=verificacion&vtab=aprobado" class="vtab-btn <?= $vtab === 'aprobado' ? 'active' : '' ?>" onclick="return irVtab(event,'aprobado')">
            ✅ Aprobados <span class="badge badge-green"><?= $porEstadoVerif['aprobado'] ?></span>
        </a>
        <a href="?tab=verificacion&vtab=rechazado" class="vtab-btn <?= $vtab === 'rechazado' ? 'active' : '' ?>" onclick="return irVtab(event,'rechazado')">
            ⛔ Rechazados <span class="badge badge-red"><?= $porEstadoVerif['rechazado'] ?></span>
        </a>
    </div>

    <?php if (empty($productosVerif)): ?>
        <div class="panel-card">
            <div class="empty-state">
                <span>📭</span>
                <p>No hay productos en esta categoría por ahora.</p>
            </div>
        </div>
    <?php else: foreach ($productosVerif as $p): ?>
        <div class="review-card" id="prod-<?= $p['id'] ?>">
            <?php if (!empty($p['imagen']) && file_exists($p['imagen'])): ?>
                <img src="<?= htmlspecialchars($p['imagen']) ?>" class="review-thumb">
            <?php else: ?>
                <div class="review-thumb">📦</div>
            <?php endif; ?>

            <div class="review-info">
                <h4><?= htmlspecialchars($p['nombre']) ?> — <span style="color:var(--gold)">$<?= number_format($p['precio'], 0, ',', '.') ?></span></h4>
                <div class="meta">
                    🏪 <?= htmlspecialchars($p['tienda_nombre']) ?> ·
                    👤 <?= htmlspecialchars($p['emprendedor_nombre']) ?> (<?= htmlspecialchars($p['emprendedor_correo']) ?>)<br>
                    🏷️ <?= htmlspecialchars($p['cat_nombre'] ?? 'Sin categoría') ?> ·
                    📦 Stock: <?= $p['stock'] ?> ·
                    🗓️ Subido: <?= date('d/m/Y H:i', strtotime($p['creado_en'])) ?>
                    <?php if ($p['descripcion']): ?><br>📝 <?= htmlspecialchars(mb_strimwidth($p['descripcion'], 0, 160, '…')) ?><?php endif; ?>
                    <?php if ($vtab === 'rechazado' && $p['motivo_rechazo']): ?>
                        <br><span style="color:#b91c1c">⛔ Motivo: <?= htmlspecialchars($p['motivo_rechazo']) ?></span>
                    <?php endif; ?>
                </div>
            </div>

            <div class="review-actions">
                <?php if ($vtab !== 'aprobado'): ?>
                    <button class="btn btn-gold btn-sm" onclick="aprobarProducto(<?= $p['id'] ?>)">✅ Aprobar</button>
                <?php endif; ?>
                <?php if ($vtab !== 'rechazado'): ?>
                    <button class="btn btn-red btn-sm" onclick="abrirRechazo(<?= $p['id'] ?>)">⛔ Rechazar</button>
                <?php endif; ?>
                <a href="tienda.php?id=<?= $p['tienda_id'] ?>" target="_blank" class="btn btn-outline btn-sm">🏪 Ver tienda</a>
            </div>
        </div>
    <?php endforeach; endif; ?>
</div><!-- /verificacion -->

<!-- Modal de rechazo -->
<div class="modal-overlay" id="modalRechazo">
    <div class="modal-box">
        <h3>Motivo del rechazo</h3>
        <textarea id="motivoRechazo" placeholder="Ej: La imagen no corresponde al producto, la descripción es engañosa, precio sospechoso, etc."></textarea>
        <div class="row">
            <button class="btn btn-outline btn-sm" onclick="cerrarRechazo()">Cancelar</button>
            <button class="btn btn-red btn-sm" onclick="confirmarRechazo()">Rechazar producto</button>
        </div>
    </div>
</div>

<!-- ══════════ REPORTES ══════════ -->
<div id="reportes" class="seccion">
    <div class="sec-header">
        <h2>📊 Reportes de ventas</h2>
    </div>

    <form class="filter-bar" method="GET">
        <input type="hidden" name="tab" value="reportes">
        <div class="form-group">
            <label>Desde</label>
            <input type="date" name="desde" value="<?= htmlspecialchars($desde) ?>">
        </div>
        <div class="form-group">
            <label>Hasta</label>
            <input type="date" name="hasta" value="<?= htmlspecialchars($hasta) ?>">
        </div>
        <div class="form-group">
            <label>Estado del pedido</label>
            <select name="estado">
                <option value="">Todos</option>
                <?php foreach ($estados_validos as $e): ?>
                    <option value="<?= $e ?>" <?= $estado_f === $e ? 'selected' : '' ?>><?= ucfirst($e) ?></option>
                <?php endforeach; ?>
            </select>
        </div>
        <div class="form-group">
            <label>Tienda</label>
            <select name="tienda_id">
                <option value="0">Todas las tiendas</option>
                <?php foreach ($tiendasList as $t): ?>
                    <option value="<?= $t['id'] ?>" <?= $tienda_f === (int)$t['id'] ? 'selected' : '' ?>><?= htmlspecialchars($t['nombre']) ?></option>
                <?php endforeach; ?>
            </select>
        </div>
        <button type="submit" class="btn btn-gold">Filtrar</button>
        <a class="btn btn-outline" href="reporte_csv.php?desde=<?= urlencode($desde) ?>&hasta=<?= urlencode($hasta) ?>&estado=<?= urlencode($estado_f) ?>&tienda_id=<?= $tienda_f ?>">⬇️ CSV</a>
    </form>

    <div class="stats">
        <div class="stat-card">
            <div class="stat-label">Ventas en el período</div>
            <div class="stat-value" style="font-size:20px;margin-top:4px">$<?= number_format($totalVentasP, 0, ',', '.') ?></div>
        </div>
        <div class="stat-card">
            <div class="stat-label">Pedidos</div>
            <div class="stat-value"><?= $totalPedidosP ?></div>
        </div>
        <div class="stat-card">
            <div class="stat-label">Ticket promedio</div>
            <div class="stat-value" style="font-size:20px;margin-top:4px">$<?= number_format($ticketProm, 0, ',', '.') ?></div>
        </div>
    </div>

    <div class="panel-card">
        <h2 style="font-size:16px;margin-bottom:18px">Ventas de los últimos 14 días (todas las tiendas)</h2>
        <div style="display:flex;align-items:flex-end;gap:6px;height:140px">
            <?php foreach ($ultimos14 as $d): ?>
            <div style="flex:1;display:flex;flex-direction:column;align-items:center;justify-content:flex-end;height:100%">
                <div style="font-size:9px;color:var(--muted);margin-bottom:6px"><?= $d['total'] > 0 ? '$' . number_format($d['total'], 0, ',', '.') : '' ?></div>
                <div style="width:100%;max-width:28px;border-radius:6px 6px 0 0;background:linear-gradient(180deg,var(--gold),#a8832a);height:<?= max(4, round($d['total'] / $maxVentaDia * 100)) ?>%"></div>
                <div style="font-size:10px;color:var(--muted);margin-top:8px"><?= $d['label'] ?></div>
            </div>
            <?php endforeach; ?>
        </div>
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:20px">
        <div class="panel-card">
            <h2 style="font-size:16px">🏪 <?= $tienda_f > 0 ? 'Tienda seleccionada' : 'Top tiendas del período' ?></h2>
            <?php if ($tienda_f > 0): ?>
                <p style="color:var(--muted);font-size:13px">El ranking de tiendas solo aplica cuando eliges "Todas las tiendas".</p>
            <?php elseif (empty($topTiendas)): ?>
                <div class="empty-state" style="padding:20px"><p>Sin ventas en este período.</p></div>
            <?php else: foreach ($topTiendas as $t): ?>
                <div style="display:flex;justify-content:space-between;padding:9px 0;border-bottom:1px solid var(--border)">
                    <div>
                        <div style="font-weight:600;font-size:13px"><?= htmlspecialchars($t['nombre']) ?></div>
                        <div style="font-size:12px;color:var(--muted)"><?= $t['pedidos'] ?> pedidos</div>
                    </div>
                    <div style="font-weight:700;font-family:'Playfair Display',serif;color:var(--gold);font-size:13px">$<?= number_format($t['total'], 0, ',', '.') ?></div>
                </div>
            <?php endforeach; endif; ?>
        </div>

        <div class="panel-card">
            <h2 style="font-size:16px">🏆 Top productos del período</h2>
            <?php if (empty($topProductos)): ?>
                <div class="empty-state" style="padding:20px"><p>Sin ventas en este período.</p></div>
            <?php else: foreach (array_slice($topProductos, 0, 5) as $mv): ?>
                <div style="display:flex;justify-content:space-between;padding:9px 0;border-bottom:1px solid var(--border)">
                    <div>
                        <div style="font-weight:600;font-size:13px"><?= htmlspecialchars($mv['nombre']) ?></div>
                        <div style="font-size:12px;color:var(--muted)"><?= $mv['cantidad'] ?> vendidos</div>
                    </div>
                    <div style="font-weight:700;font-family:'Playfair Display',serif;color:var(--gold);font-size:13px">$<?= number_format($mv['total'], 0, ',', '.') ?></div>
                </div>
            <?php endforeach; endif; ?>
        </div>
    </div>
</div><!-- /reportes -->

<!-- ══════════ FACTURAS ══════════ -->
<div id="facturas" class="seccion">
    <div class="sec-header">
        <h2>🧾 Facturas</h2>
    </div>

    <form class="factura-search" method="GET">
        <input type="hidden" name="tab" value="facturas">
        <input type="text" name="f_id" placeholder="Buscar por # de pedido" value="<?= htmlspecialchars($busq_id) ?>">
        <input type="text" name="f_cliente" placeholder="Buscar por nombre de cliente" value="<?= htmlspecialchars($busq_cliente) ?>">
        <button type="submit" class="btn btn-gold btn-sm">Buscar</button>
    </form>

    <div class="panel-card" style="padding:0;overflow:hidden">
        <table class="prod-table">
            <thead>
                <tr>
                    <th>Factura</th>
                    <th>Cliente</th>
                    <th>Fecha</th>
                    <th>Estado</th>
                    <th>Total</th>
                    <th></th>
                </tr>
            </thead>
            <tbody>
                <?php if (empty($facturasList)): ?>
                <tr><td colspan="6"><div class="empty-state"><p>No se encontraron pedidos.</p></div></td></tr>
                <?php else: foreach ($facturasList as $f): ?>
                <tr>
                    <td style="font-weight:600">FAC-<?= str_pad($f['id'], 6, '0', STR_PAD_LEFT) ?></td>
                    <td><?= htmlspecialchars($f['cliente_nombre']) ?></td>
                    <td><?= date('d/m/Y H:i', strtotime($f['creado_en'])) ?></td>
                    <td><span class="badge badge-<?= $f['estado'] === 'entregado' ? 'green' : ($f['estado'] === 'cancelado' ? 'red' : 'gold') ?>"><?= ucfirst($f['estado']) ?></span></td>
                    <td style="font-weight:700;color:var(--gold)">$<?= number_format($f['total'], 0, ',', '.') ?></td>
                    <td><a href="factura.php?id=<?= $f['id'] ?>" target="_blank" class="btn btn-outline btn-sm">Ver factura</a></td>
                </tr>
                <?php endforeach; endif; ?>
            </tbody>
        </table>
    </div>
</div><!-- /facturas -->

<!-- ══════════ USUARIOS ══════════ -->
<div id="usuarios" class="seccion">
    <div class="sec-header">
        <h2>👥 Usuarios</h2>
    </div>

    <form class="filter-bar" method="GET">
        <input type="hidden" name="tab" value="usuarios">
        <div class="form-group">
            <label>Rol</label>
            <select name="rol">
                <option value="">Todos</option>
                <option value="cliente" <?= $rol_f === 'cliente' ? 'selected' : '' ?>>Cliente</option>
                <option value="emprendedor" <?= $rol_f === 'emprendedor' ? 'selected' : '' ?>>Emprendedor</option>
                <option value="administrador" <?= $rol_f === 'administrador' ? 'selected' : '' ?>>Administrador</option>
            </select>
        </div>
        <div class="form-group" style="flex:2">
            <label>Buscar</label>
            <input type="text" name="u_buscar" placeholder="Nombre o correo" value="<?= htmlspecialchars($buscar_u) ?>">
        </div>
        <button type="submit" class="btn btn-gold">Filtrar</button>
    </form>

    <div class="panel-card" style="padding:0;overflow:hidden">
        <table class="prod-table">
            <thead>
                <tr><th>Nombre</th><th>Correo</th><th>Rol</th><th>Estado</th><th></th></tr>
            </thead>
            <tbody>
                <?php if (empty($usuariosList)): ?>
                <tr><td colspan="5"><div class="empty-state"><p>No se encontraron usuarios.</p></div></td></tr>
                <?php else: foreach ($usuariosList as $u): ?>
                <tr id="user-<?= $u['id'] ?>">
                    <td style="font-weight:600"><?= htmlspecialchars($u['nombre']) ?></td>
                    <td><?= htmlspecialchars($u['correo']) ?></td>
                    <td><span class="badge badge-<?= $u['rol']==='administrador'?'purple':($u['rol']==='emprendedor'?'blue':'gray') ?>"><?= ucfirst($u['rol']) ?></span></td>
                    <td><span class="badge badge-<?= $u['activo'] ? 'green':'red' ?>"><?= $u['activo'] ? 'Activo':'Suspendido' ?></span></td>
                    <td>
                        <?php if ((int)$u['id'] !== $admin_id && $u['rol'] !== 'administrador'): ?>
                            <?php if ($u['activo']): ?>
                                <button class="btn btn-red btn-sm" onclick="toggleUsuario(<?= $u['id'] ?>, 0)">Suspender</button>
                            <?php else: ?>
                                <button class="btn btn-gold btn-sm" onclick="toggleUsuario(<?= $u['id'] ?>, 1)">Reactivar</button>
                            <?php endif; ?>
                        <?php else: ?>
                            <span style="color:var(--muted);font-size:12px">—</span>
                        <?php endif; ?>
                    </td>
                </tr>
                <?php endforeach; endif; ?>
            </tbody>
        </table>
    </div>
</div><!-- /usuarios -->

<!-- ══════════ CATEGORÍAS ══════════ -->
<div id="categorias" class="seccion">
    <div class="sec-header">
        <h2>🏷️ Categorías</h2>
        <button class="btn btn-gold btn-sm" onclick="abrirCategoria()">+ Nueva categoría</button>
    </div>

    <div class="panel-card" style="padding:0;overflow:hidden">
        <table class="prod-table">
            <thead>
                <tr><th>Icono</th><th>Nombre</th><th>Productos</th><th></th></tr>
            </thead>
            <tbody>
                <?php if (empty($categoriasList)): ?>
                <tr><td colspan="4"><div class="empty-state"><p>No hay categorías todavía.</p></div></td></tr>
                <?php else: foreach ($categoriasList as $c): ?>
                <tr id="cat-<?= $c['id'] ?>">
                    <td style="font-size:20px"><?= htmlspecialchars($c['icono'] ?: '🏷️') ?></td>
                    <td style="font-weight:600"><?= htmlspecialchars($c['nombre']) ?></td>
                    <td><?= $c['total_productos'] ?></td>
                    <td style="display:flex;gap:8px">
                        <button class="btn btn-outline btn-sm" onclick='abrirCategoria(<?= (int)$c["id"] ?>, <?= json_encode($c["nombre"]) ?>, <?= json_encode($c["icono"]) ?>)'>Editar</button>
                        <button class="btn btn-red btn-sm" onclick="eliminarCategoria(<?= $c['id'] ?>, <?= $c['total_productos'] ?>)">Eliminar</button>
                    </td>
                </tr>
                <?php endforeach; endif; ?>
            </tbody>
        </table>
    </div>
</div><!-- /categorias -->

<!-- ══════════ TIENDAS ══════════ -->
<div id="tiendas" class="seccion">
    <div class="sec-header">
        <h2>🏪 Tiendas</h2>
    </div>

    <form class="filter-bar" method="GET">
        <input type="hidden" name="tab" value="tiendas">
        <div class="form-group" style="flex:2">
            <label>Buscar</label>
            <input type="text" name="t_buscar" placeholder="Nombre de tienda, dueño o correo" value="<?= htmlspecialchars($buscar_t) ?>">
        </div>
        <button type="submit" class="btn btn-gold">Filtrar</button>
    </form>

    <div class="panel-card" style="padding:0;overflow:hidden">
        <table class="prod-table">
            <thead>
                <tr><th>Tienda</th><th>Dueño</th><th>Productos</th><th>Ventas</th><th>Estado</th><th></th></tr>
            </thead>
            <tbody>
                <?php if (empty($tiendasGestion)): ?>
                <tr><td colspan="6"><div class="empty-state"><p>No se encontraron tiendas.</p></div></td></tr>
                <?php else: foreach ($tiendasGestion as $t): ?>
                <tr id="tienda-<?= $t['id'] ?>">
                    <td style="font-weight:600"><?= htmlspecialchars($t['nombre']) ?></td>
                    <td><?= htmlspecialchars($t['dueno_nombre']) ?><br><span style="color:var(--muted);font-size:12px"><?= htmlspecialchars($t['dueno_correo']) ?></span></td>
                    <td><?= $t['total_productos'] ?></td>
                    <td style="font-weight:700;color:var(--gold)">$<?= number_format($t['total_ventas'], 0, ',', '.') ?></td>
                    <td>
                        <span class="badge badge-<?= $t['activo'] ? 'green':'red' ?>"><?= $t['activo'] ? 'Activa':'Suspendida' ?></span>
                        <?php if (!$t['activo'] && $t['motivo_suspension']): ?>
                            <div style="font-size:11px;color:var(--muted);margin-top:4px">Motivo: <?= htmlspecialchars($t['motivo_suspension']) ?></div>
                        <?php endif; ?>
                    </td>
                    <td style="display:flex;gap:8px">
                        <a href="tienda.php?id=<?= $t['id'] ?>" target="_blank" class="btn btn-outline btn-sm">Ver tienda</a>
                        <?php if ($t['activo']): ?>
                            <button class="btn btn-red btn-sm" onclick="abrirSuspenderTienda(<?= $t['id'] ?>)">Suspender</button>
                        <?php else: ?>
                            <button class="btn btn-gold btn-sm" onclick="reactivarTienda(<?= $t['id'] ?>)">Reactivar</button>
                        <?php endif; ?>
                    </td>
                </tr>
                <?php endforeach; endif; ?>
            </tbody>
        </table>
    </div>
</div><!-- /tiendas -->

<div class="modal-overlay" id="modalSuspenderTienda">
    <div class="modal-box">
        <h3>Motivo de la suspensión</h3>
        <textarea id="motivoSuspensionTienda" placeholder="Ej: Productos falsos, incumplimiento de pedidos, reportes de fraude, etc."></textarea>
        <div class="row">
            <button class="btn btn-outline btn-sm" onclick="cerrarSuspenderTienda()">Cancelar</button>
            <button class="btn btn-red btn-sm" onclick="confirmarSuspenderTienda()">Suspender tienda</button>
        </div>
    </div>
</div>

<div class="modal-overlay" id="modalCategoria">
    <div class="modal-box">
        <h3 id="tituloModalCategoria">Nueva categoría</h3>
        <input type="hidden" id="catId" value="">
        <label style="display:block;font-size:12px;color:var(--muted);margin-bottom:6px">Nombre</label>
        <input type="text" id="catNombre" placeholder="Ej: Joyería" style="width:100%;margin-bottom:14px">
        <label style="display:block;font-size:12px;color:var(--muted);margin-bottom:6px">Icono (emoji)</label>
        <input type="text" id="catIcono" placeholder="Ej: 💍" style="width:100%;margin-bottom:16px">
        <div class="row">
            <button class="btn btn-outline btn-sm" onclick="cerrarCategoria()">Cancelar</button>
            <button class="btn btn-gold btn-sm" onclick="guardarCategoria()">Guardar</button>
        </div>
    </div>
</div>

</div><!-- /main -->

<script>
function irA(id, elNav) {
    document.querySelectorAll('.seccion').forEach(s => s.classList.remove('activa'));
    document.getElementById(id).classList.add('activa');
    document.querySelectorAll('.sb-item').forEach(i => i.classList.remove('active'));
    if (elNav && elNav.classList) elNav.classList.add('active');
}

document.addEventListener('DOMContentLoaded', () => {
    const params = new URLSearchParams(window.location.search);
    const tab = params.get('tab');
    if (tab) {
        const nav = document.querySelector('.sb-item[data-tab="' + tab + '"]');
        irA(tab, nav);
    }
});

function irVtab(ev, v) {
    return true;
}

/* ── Aprobar / Rechazar productos ── */
function aprobarProducto(id) {
    if (!confirm('¿Aprobar este producto? Será visible para todos los clientes.')) return;
    enviarVerificacion(id, 'aprobar', '');
}

let productoRechazoActual = null;
function abrirRechazo(id) {
    productoRechazoActual = id;
    document.getElementById('motivoRechazo').value = '';
    document.getElementById('modalRechazo').classList.add('open');
}
function cerrarRechazo() {
    document.getElementById('modalRechazo').classList.remove('open');
    productoRechazoActual = null;
}
function confirmarRechazo() {
    const motivo = document.getElementById('motivoRechazo').value.trim();
    if (!motivo) { alert('Indica un motivo de rechazo.'); return; }
    enviarVerificacion(productoRechazoActual, 'rechazar', motivo);
    cerrarRechazo();
}

function enviarVerificacion(id, accion, motivo) {
    fetch('verificar_producto.php', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `producto_id=${id}&accion=${accion}&motivo=${encodeURIComponent(motivo)}`
    })
    .then(r => r.json())
    .then(data => {
        if (data.ok) {
            const card = document.getElementById('prod-' + id);
            if (card) card.remove();
        } else {
            alert(data.error || 'No se pudo actualizar el producto.');
        }
    })
    .catch(() => alert('Error de conexión al actualizar el producto.'));
}

/* ── Usuarios ── */
function toggleUsuario(id, activo) {
    const accion = activo ? 'reactivar' : 'suspender';
    if (!confirm(`¿Seguro que quieres ${accion} a este usuario?`)) return;
    fetch('gestionar_usuario.php', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `usuario_id=${id}&activo=${activo}`
    })
    .then(r => r.json())
    .then(data => {
        if (data.ok) location.reload();
        else alert(data.error || 'No se pudo actualizar el usuario.');
    })
    .catch(() => alert('Error de conexión.'));
}

/* ── Categorías ── */
function abrirCategoria(id, nombre, icono) {
    document.getElementById('catId').value = id || '';
    document.getElementById('catNombre').value = nombre || '';
    document.getElementById('catIcono').value = icono || '';
    document.getElementById('tituloModalCategoria').textContent = id ? 'Editar categoría' : 'Nueva categoría';
    document.getElementById('modalCategoria').classList.add('open');
}
function cerrarCategoria() {
    document.getElementById('modalCategoria').classList.remove('open');
}
function guardarCategoria() {
    const id = document.getElementById('catId').value;
    const nombre = document.getElementById('catNombre').value.trim();
    const icono = document.getElementById('catIcono').value.trim();
    if (!nombre) { alert('El nombre es obligatorio.'); return; }
    fetch('gestionar_categoria.php', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `accion=guardar&id=${id}&nombre=${encodeURIComponent(nombre)}&icono=${encodeURIComponent(icono)}`
    })
    .then(r => r.json())
    .then(data => {
        if (data.ok) location.reload();
        else alert(data.error || 'No se pudo guardar la categoría.');
    })
    .catch(() => alert('Error de conexión.'));
}
function eliminarCategoria(id, totalProductos) {
    if (totalProductos > 0) {
        alert('No puedes eliminar una categoría que todavía tiene productos asignados. Reasigna esos productos primero.');
        return;
    }
    if (!confirm('¿Eliminar esta categoría?')) return;
    fetch('gestionar_categoria.php', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `accion=eliminar&id=${id}`
    })
    .then(r => r.json())
    .then(data => {
        if (data.ok) { const row = document.getElementById('cat-' + id); if (row) row.remove(); }
        else alert(data.error || 'No se pudo eliminar.');
    })
    .catch(() => alert('Error de conexión.'));
}

/* ── Tiendas ── */
let tiendaSuspenderActual = null;
function abrirSuspenderTienda(id) {
    tiendaSuspenderActual = id;
    document.getElementById('motivoSuspensionTienda').value = '';
    document.getElementById('modalSuspenderTienda').classList.add('open');
}
function cerrarSuspenderTienda() {
    document.getElementById('modalSuspenderTienda').classList.remove('open');
    tiendaSuspenderActual = null;
}
function confirmarSuspenderTienda() {
    const motivo = document.getElementById('motivoSuspensionTienda').value.trim();
    if (!motivo) { alert('Indica un motivo de suspensión.'); return; }
    enviarTienda(tiendaSuspenderActual, 0, motivo);
    cerrarSuspenderTienda();
}
function reactivarTienda(id) {
    if (!confirm('¿Reactivar esta tienda? Sus productos aprobados volverán a ser visibles.')) return;
    enviarTienda(id, 1, '');
}
function enviarTienda(id, activo, motivo) {
    fetch('gestionar_tienda.php', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `tienda_id=${id}&activo=${activo}&motivo=${encodeURIComponent(motivo)}`
    })
    .then(r => r.json())
    .then(data => {
        if (data.ok) location.reload();
        else alert(data.error || 'No se pudo actualizar la tienda.');
    })
    .catch(() => alert('Error de conexión.'));
}

const flash = document.querySelector('.flash');
if (flash) setTimeout(() => flash.style.opacity = '0', 4000);
</script>
</body>
</html>