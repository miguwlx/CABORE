<?php
session_start();
require_once 'conexion.php';

if (!isset($_SESSION['usuario_id']) || $_SESSION['rol'] !== 'cliente') {
    header('Location: login.html');
    exit;
}

$usuario_id = $_SESSION['usuario_id'];

$stmt = $pdo->prepare("SELECT * FROM usuarios WHERE id = ?");
$stmt->execute([$usuario_id]);
$usuario = $stmt->fetch();

// Pedidos del usuario
$stmt = $pdo->prepare("
    SELECT p.*,
           COUNT(pi.id) AS total_items
    FROM pedidos p
    LEFT JOIN pedido_items pi ON pi.pedido_id = p.id
    WHERE p.usuario_id = ?
    GROUP BY p.id
    ORDER BY p.created_at DESC
");
$stmt->execute([$usuario_id]);
$pedidos = $stmt->fetchAll();

// Ítems de un pedido específico (para modal)
$pedido_detalle = null;
if (isset($_GET['ver'])) {
    $pid = intval($_GET['ver']);
    $stmt = $pdo->prepare("
        SELECT pi.*, pr.nombre, pr.imagen, t.nombre AS tienda_nombre, t.color AS tienda_color
        FROM pedido_items pi
        JOIN productos pr ON pi.producto_id = pr.id
        JOIN tiendas t ON pr.tienda_id = t.id
        WHERE pi.pedido_id = ?
    ");
    $stmt->execute([$pid]);
    $pedido_detalle_items = $stmt->fetchAll();

    $stmt2 = $pdo->prepare("SELECT * FROM pedidos WHERE id = ? AND usuario_id = ?");
    $stmt2->execute([$pid, $usuario_id]);
    $pedido_detalle = $stmt2->fetch();
}

// Flash
$flash = $_SESSION['flash'] ?? null;
unset($_SESSION['flash']);

$ultimo_pedido = $_SESSION['ultimo_pedido'] ?? null;
unset($_SESSION['ultimo_pedido']);

$estado_labels = [
    'pendiente'  => ['label' => 'Pendiente',   'icon' => 'fa-clock',          'color' => '#D4A017', 'bg' => '#FEF9E7'],
    'confirmado' => ['label' => 'Confirmado',   'icon' => 'fa-check-circle',   'color' => '#2A7A4B', 'bg' => '#E0F5EB'],
    'enviado'    => ['label' => 'En camino',    'icon' => 'fa-truck',          'color' => '#2874A6', 'bg' => '#EBF5FB'],
    'entregado'  => ['label' => 'Entregado',    'icon' => 'fa-box-open',       'color' => '#7D3C98', 'bg' => '#F5EEF8'],
    'cancelado'  => ['label' => 'Cancelado',    'icon' => 'fa-times-circle',   'color' => '#C0392B', 'bg' => '#FDECEA'],
];
?>
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Caboré — Mis pedidos</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&family=Fraunces:ital,opsz,wght@0,9..144,300;0,9..144,600&display=swap" rel="stylesheet">
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css">
<style>
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
:root {
    --bg:       #F7F5F2;
    --surface:  #FFFFFF;
    --border:   #E8E4DF;
    --text:     #1A1612;
    --muted:    #7A736C;
    --accent:   #C85A2A;
    --accent-lt:#F5E8E0;
    --green:    #2A7A4B;
    --green-lt: #E0F5EB;
    --radius:   12px;
    --shadow:   0 2px 12px rgba(0,0,0,.07);
    --shadow-lg:0 8px 32px rgba(0,0,0,.12);
    --font-head:'Fraunces', Georgia, serif;
    --font-body:'Plus Jakarta Sans', system-ui, sans-serif;
}
body { font-family: var(--font-body); background: var(--bg); color: var(--text); min-height: 100vh; }
a { color: inherit; text-decoration: none; }

.navbar {
    height: 64px;
    background: var(--surface);
    border-bottom: 1px solid var(--border);
    display: flex;
    align-items: center;
    padding: 0 28px;
    gap: 16px;
    position: sticky;
    top: 0;
    z-index: 10;
}
.nav-logo { font-family: var(--font-head); font-size: 1.5rem; font-weight: 600; color: var(--accent); }
.nav-links { display: flex; gap: 6px; margin-left: auto; }
.nav-link {
    display: flex;
    align-items: center;
    gap: 7px;
    padding: 8px 14px;
    border-radius: 8px;
    font-size: .85rem;
    color: var(--muted);
    transition: background .15s, color .15s;
}
.nav-link:hover { background: var(--bg); color: var(--accent); }
.nav-link.active { background: var(--accent-lt); color: var(--accent); font-weight: 600; }

.page {
    max-width: 840px;
    margin: 40px auto;
    padding: 0 20px 60px;
}

.page-header { margin-bottom: 28px; }
.page-header h1 { font-family: var(--font-head); font-size: 1.8rem; font-weight: 600; margin-bottom: 4px; }
.page-header p { color: var(--muted); font-size: .9rem; }

.flash {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 14px 18px;
    border-radius: var(--radius);
    margin-bottom: 24px;
    font-size: .9rem;
    font-weight: 500;
}
.flash.ok  { background: var(--green-lt); color: var(--green); }
.flash.err { background: #FDECEA; color: #C0392B; }

/* Pedido nuevo highlight */
.pedido-nuevo-banner {
    background: var(--green-lt);
    border: 1.5px solid var(--green);
    border-radius: var(--radius);
    padding: 18px 22px;
    display: flex;
    align-items: center;
    gap: 14px;
    margin-bottom: 28px;
}
.pedido-nuevo-banner i { font-size: 1.8rem; color: var(--green); }
.pedido-nuevo-banner h3 { font-size: 1rem; font-weight: 700; color: var(--green); margin-bottom: 2px; }
.pedido-nuevo-banner p  { font-size: .85rem; color: var(--green); opacity: .85; }

/* Pedido card */
.pedido-card {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 14px;
    margin-bottom: 16px;
    overflow: hidden;
    transition: box-shadow .2s;
}
.pedido-card:hover { box-shadow: var(--shadow); }

.pedido-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 10px;
    padding: 16px 22px;
    border-bottom: 1px solid var(--border);
    cursor: pointer;
}
.pedido-num {
    font-weight: 700;
    font-size: .95rem;
    display: flex;
    align-items: center;
    gap: 8px;
}
.pedido-num span { font-weight: 400; color: var(--muted); font-size: .8rem; }

.estado-badge {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 4px 12px;
    border-radius: 99px;
    font-size: .78rem;
    font-weight: 700;
}

.pedido-meta {
    display: flex;
    align-items: center;
    gap: 18px;
    flex-wrap: wrap;
}
.pedido-meta-item { font-size: .82rem; color: var(--muted); display: flex; align-items: center; gap: 5px; }
.pedido-meta-item strong { color: var(--text); font-weight: 600; }

.pedido-toggle {
    background: none;
    border: none;
    color: var(--muted);
    font-size: .9rem;
    padding: 4px;
    transition: transform .2s, color .15s;
}
.pedido-toggle:hover { color: var(--accent); }
.pedido-toggle.open { transform: rotate(180deg); color: var(--accent); }

.pedido-body {
    display: none;
    padding: 18px 22px;
}
.pedido-body.open { display: block; }

.pedido-items-list { list-style: none; margin-bottom: 16px; }
.pedido-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 0;
    border-bottom: 1px solid var(--border);
}
.pedido-item:last-child { border-bottom: none; }
.pedido-item-img {
    width: 48px;
    height: 48px;
    object-fit: cover;
    border-radius: 8px;
    background: var(--bg);
    flex-shrink: 0;
}
.pedido-item-no-img {
    width: 48px;
    height: 48px;
    border-radius: 8px;
    background: var(--bg);
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--border);
    font-size: 1.1rem;
    flex-shrink: 0;
}
.pedido-item-info { flex: 1; }
.pedido-item-name { font-size: .875rem; font-weight: 600; margin-bottom: 2px; }
.pedido-item-store { font-size: .75rem; color: var(--muted); display: flex; align-items: center; gap: 4px; }
.pedido-item-dot { width: 7px; height: 7px; border-radius: 50%; display: inline-block; }
.pedido-item-qty { font-size: .75rem; color: var(--muted); margin-top: 1px; }
.pedido-item-price { font-size: .9rem; font-weight: 700; }

.pedido-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 12px;
    padding-top: 14px;
    border-top: 1px solid var(--border);
    margin-top: 4px;
}
.pedido-dir { font-size: .82rem; color: var(--muted); display: flex; align-items: center; gap: 6px; }
.pedido-total-label { font-size: .82rem; color: var(--muted); }
.pedido-total-val { font-size: 1.1rem; font-weight: 700; }

/* Empty */
.empty {
    text-align: center;
    padding: 70px 20px;
    color: var(--muted);
}
.empty i { font-size: 3.5rem; opacity: .25; margin-bottom: 16px; display: block; }
.empty h3 { font-size: 1.1rem; font-weight: 600; color: var(--text); margin-bottom: 8px; }
.empty p { font-size: .875rem; margin-bottom: 20px; }
.btn-primary {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 11px 22px;
    background: var(--accent);
    color: #fff;
    border-radius: 9px;
    font-size: .9rem;
    font-weight: 600;
    transition: background .2s;
}
.btn-primary:hover { background: #A8431C; }

@media (max-width: 540px) {
    .page { padding: 0 12px 40px; }
    .pedido-head { padding: 12px 16px; }
    .pedido-body { padding: 14px 16px; }
}
</style>
</head>
<body>

<nav class="navbar">
    <a href="cliente.php" class="nav-logo">Caboré</a>
    <div class="nav-links">
        <a href="cliente.php"    class="nav-link"><i class="fas fa-store"></i> Tiendas</a>
        <a href="favoritos.php"  class="nav-link"><i class="far fa-heart"></i> Favoritos</a>
        <a href="mis-pedidos.php" class="nav-link active"><i class="fas fa-box"></i> Mis pedidos</a>
        <a href="logout.php"     class="nav-link"><i class="fas fa-sign-out-alt"></i> Salir</a>
    </div>
</nav>

<div class="page">

    <div class="page-header">
        <h1>Mis pedidos</h1>
        <p>Hola <?= htmlspecialchars($usuario['nombre']) ?>, aquí está el historial de tus compras.</p>
    </div>

    <?php if ($flash): ?>
    <div class="flash <?= $flash['tipo'] ?>">
        <i class="fas <?= $flash['tipo']==='ok' ? 'fa-check-circle' : 'fa-exclamation-circle' ?>"></i>
        <?= htmlspecialchars($flash['msg']) ?>
    </div>
    <?php endif; ?>

    <?php if ($ultimo_pedido): ?>
    <div class="pedido-nuevo-banner">
        <i class="fas fa-party-horn"></i>
        <div>
            <h3>¡Pedido #<?= $ultimo_pedido ?> realizado!</h3>
            <p>El emprendedor recibirá tu orden y se comunicará contigo pronto para coordinar el envío.</p>
        </div>
    </div>
    <?php endif; ?>

    <?php if (empty($pedidos)): ?>
    <div class="empty">
        <i class="fas fa-box-open"></i>
        <h3>Aún no tienes pedidos</h3>
        <p>Explora las tiendas y haz tu primera compra.</p>
        <a href="cliente.php" class="btn-primary"><i class="fas fa-store"></i> Explorar tiendas</a>
    </div>
    <?php else: ?>

    <?php foreach ($pedidos as $pedido): ?>
    <?php
        $est = $estado_labels[$pedido['estado']] ?? $estado_labels['pendiente'];

        // Obtener ítems de este pedido
        $stmt_items = $pdo->prepare("
            SELECT pi.*, pr.nombre, pr.imagen, t.nombre AS tienda_nombre, t.color AS tienda_color
            FROM pedido_items pi
            JOIN productos pr ON pi.producto_id = pr.id
            JOIN tiendas t ON pr.tienda_id = t.id
            WHERE pi.pedido_id = ?
        ");
        $stmt_items->execute([$pedido['id']]);
        $items = $stmt_items->fetchAll();
    ?>
    <div class="pedido-card" id="card-<?= $pedido['id'] ?>">

        <div class="pedido-head" onclick="togglePedido(<?= $pedido['id'] ?>)">
            <div>
                <div class="pedido-num">
                    Pedido #<?= $pedido['id'] ?>
                    <span><?= date('d/m/Y H:i', strtotime($pedido['created_at'])) ?></span>
                </div>
                <div class="pedido-meta" style="margin-top:6px">
                    <div class="pedido-meta-item">
                        <i class="fas fa-box"></i>
                        <strong><?= $pedido['total_items'] ?></strong> producto<?= $pedido['total_items']!=1?'s':'' ?>
                    </div>
                    <div class="pedido-meta-item">
                        <i class="fas fa-dollar-sign"></i>
                        Total: <strong>$<?= number_format($pedido['total'], 0, ',', '.') ?></strong>
                    </div>
                </div>
            </div>
            <div style="display:flex;align-items:center;gap:12px">
                <span class="estado-badge" style="background:<?= $est['bg'] ?>;color:<?= $est['color'] ?>">
                    <i class="fas <?= $est['icon'] ?>"></i> <?= $est['label'] ?>
                </span>
                <button class="pedido-toggle" id="toggle-<?= $pedido['id'] ?>">
                    <i class="fas fa-chevron-down"></i>
                </button>
            </div>
        </div>

        <div class="pedido-body <?= ($ultimo_pedido == $pedido['id']) ? 'open' : '' ?>" id="body-<?= $pedido['id'] ?>">
            <ul class="pedido-items-list">
                <?php foreach ($items as $item): ?>
                <li class="pedido-item">
                    <?php if ($item['imagen']): ?>
                        <img class="pedido-item-img"
                             src="img/productos/<?= htmlspecialchars($item['imagen']) ?>"
                             alt="<?= htmlspecialchars($item['nombre']) ?>">
                    <?php else: ?>
                        <div class="pedido-item-no-img"><i class="fas fa-box-open"></i></div>
                    <?php endif; ?>
                    <div class="pedido-item-info">
                        <div class="pedido-item-name"><?= htmlspecialchars($item['nombre']) ?></div>
                        <div class="pedido-item-store">
                            <span class="pedido-item-dot" style="background:<?= htmlspecialchars($item['tienda_color'] ?: '#888') ?>"></span>
                            <?= htmlspecialchars($item['tienda_nombre']) ?>
                        </div>
                        <div class="pedido-item-qty">Cantidad: <?= $item['cantidad'] ?></div>
                    </div>
                    <div class="pedido-item-price">
                        $<?= number_format($item['precio_unitario'] * $item['cantidad'], 0, ',', '.') ?>
                    </div>
                </li>
                <?php endforeach; ?>
            </ul>

            <div class="pedido-footer">
                <?php if ($pedido['direccion_entrega']): ?>
                <div class="pedido-dir">
                    <i class="fas fa-map-marker-alt"></i>
                    <?= htmlspecialchars($pedido['direccion_entrega']) ?>
                </div>
                <?php endif; ?>
                <div>
                    <div class="pedido-total-label">Total del pedido</div>
                    <div class="pedido-total-val">$<?= number_format($pedido['total'], 0, ',', '.') ?></div>
                </div>
            </div>

            <?php if ($pedido['notas']): ?>
            <div style="margin-top:12px;font-size:.82rem;color:var(--muted)">
                <i class="fas fa-sticky-note"></i>
                <em><?= htmlspecialchars($pedido['notas']) ?></em>
            </div>
            <?php endif; ?>
        </div>

    </div>
    <?php endforeach; ?>
    <?php endif; ?>

</div>

<script>
function togglePedido(id) {
    const body   = document.getElementById('body-' + id);
    const toggle = document.getElementById('toggle-' + id);
    body.classList.toggle('open');
    toggle.classList.toggle('open');
}

// Auto abrir el último pedido
<?php if ($ultimo_pedido): ?>
const lastBody = document.getElementById('body-<?= $ultimo_pedido ?>');
const lastToggle = document.getElementById('toggle-<?= $ultimo_pedido ?>');
if (lastBody) { lastBody.classList.add('open'); }
if (lastToggle) { lastToggle.classList.add('open'); }
// Limpiar carrito localStorage
localStorage.removeItem('cabore_cart');
<?php endif; ?>
</script>
</body>
</html>