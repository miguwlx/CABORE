<?php
session_start();
require_once 'conexion.php';

if (!isset($_SESSION['usuario_id']) || $_SESSION['rol'] !== 'cliente') {
    header('Location: login.html');
    exit;
}

$usuario_id = $_SESSION['usuario_id'];

// ── Carrito SIEMPRE atado al usuario actual (evita que se mezcle entre cuentas) ──
if (!isset($_SESSION['carrito']) || !isset($_SESSION['carrito_usuario_id']) || $_SESSION['carrito_usuario_id'] !== $usuario_id) {
    $_SESSION['carrito']            = [];
    $_SESSION['carrito_usuario_id'] = $usuario_id;
}

// Datos del usuario
$stmt = $pdo->prepare("SELECT * FROM usuarios WHERE id = ?");
$stmt->execute([$usuario_id]);
$usuario = $stmt->fetch();

// Productos favoritos del usuario, con datos de tienda y rating (igual que en cliente.php)
$stmt = $pdo->prepare("
    SELECT p.*, t.nombre AS tienda_nombre, t.color AS tienda_color, t.logo AS tienda_logo,
           t.whatsapp AS tienda_wa, t.id AS tienda_id,
           c.nombre AS categoria_nombre,
           COALESCE(AVG(r.calificacion), 0) AS rating,
           COUNT(DISTINCT r.id) AS total_resenas
    FROM favoritos f
    JOIN productos p ON p.id = f.producto_id
    JOIN tiendas t   ON p.tienda_id = t.id
    LEFT JOIN categorias c ON p.categoria_id = c.id
    LEFT JOIN resenas r    ON r.producto_id = p.id
    WHERE f.usuario_id = ?
    GROUP BY p.id
    ORDER BY f.id DESC
");
$stmt->execute([$usuario_id]);
$productos = $stmt->fetchAll();

// Todos son favoritos en esta página (para reutilizar el mismo botón fav-btn de cliente.php)
$favoritos = array_column($productos, 'id');

// Contar ítems en carrito
$carrito_count = array_sum($_SESSION['carrito']);

$flash = $_SESSION['flash'] ?? null;
unset($_SESSION['flash']);
?>
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Caboré — Mis favoritos</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Playfair+Display:wght@600;700;900&family=DM+Sans:wght@300;400;500;600;700&display=swap" rel="stylesheet">
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css">
<style>
/* ── Reset & tokens (idénticos a cliente.php para mantener consistencia visual) ── */
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

:root {
    --bg:        #faf7f0;
    --surface:   #ffffff;
    --border:    rgba(184,146,60,0.28);
    --text:      #1c1810;
    --muted:     rgba(28,24,16,0.56);
    --accent:    #e2603f;
    --accent-lt: rgba(226,96,63,0.12);
    --green:     #0f9d63;
    --green-lt:  rgba(15,157,99,0.12);
    --gold:      #b8923c;
    --gold-dk:   #8c6c28;
    --radius:    14px;
    --shadow:    0 4px 16px rgba(28,24,16,.08);
    --shadow-lg: 0 24px 60px rgba(28,24,16,.14);
    --font-head: 'Playfair Display', Georgia, serif;
    --font-body: 'DM Sans', system-ui, sans-serif;
    --nav-h:     64px;
}

body {
    font-family: var(--font-body);
    background: var(--bg);
    color: var(--text);
    min-height: 100vh;
}
a { color: inherit; text-decoration: none; }

/* ── Navbar (igual a cliente.php) ────────────────────────── */
.navbar {
    height: var(--nav-h);
    background: var(--surface);
    border-bottom: 1px solid var(--border);
    display: flex;
    align-items: center;
    padding: 0 28px;
    gap: 20px;
    position: sticky;
    top: 0;
    z-index: 200;
}
.nav-logo {
    font-family: var(--font-head);
    font-size: 1.5rem;
    font-weight: 700;
    color: var(--gold-dk);
    flex-shrink: 0;
}
.nav-actions { display: flex; align-items: center; gap: 10px; margin-left: auto; }
.nav-btn {
    position: relative;
    width: 40px; height: 40px;
    border-radius: 50%;
    border: 1px solid var(--border);
    background: var(--bg);
    display: flex; align-items: center; justify-content: center;
    color: var(--text);
    font-size: .95rem;
    cursor: pointer;
    transition: background .2s, transform .2s;
}
.nav-btn:hover { background: var(--accent-lt); transform: translateY(-1px); }
.cart-badge {
    position: absolute;
    top: -4px; right: -4px;
    min-width: 18px; height: 18px;
    border-radius: 99px;
    background: var(--accent);
    color: #fff;
    font-size: .65rem;
    font-weight: 700;
    display: flex; align-items: center; justify-content: center;
    padding: 0 4px;
}
.nav-user { position: relative; }
.nav-avatar {
    width: 40px; height: 40px;
    border-radius: 50%;
    background: linear-gradient(135deg, var(--gold), var(--gold-dk));
    color: #fff;
    display: flex; align-items: center; justify-content: center;
    font-weight: 700;
    cursor: pointer;
    font-size: .9rem;
}
.nav-dropdown {
    display: none;
    position: absolute;
    top: 50px; right: 0;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 12px;
    box-shadow: var(--shadow-lg);
    min-width: 210px;
    padding: 8px;
    z-index: 250;
}
.nav-user:hover .nav-dropdown { display: block; }
.nav-dropdown a {
    display: flex; align-items: center; gap: 10px;
    padding: 9px 12px;
    border-radius: 8px;
    font-size: .85rem;
    color: var(--text);
    transition: background .15s;
}
.nav-dropdown a:hover { background: var(--bg); }
.nav-dropdown a.danger { color: var(--accent); }
.nav-dropdown hr { border: none; border-top: 1px solid var(--border); margin: 6px 0; }

/* ── Contenido ────────────────────────────────────────────── */
.page-wrap { max-width: 1240px; margin: 0 auto; padding: 32px 28px 60px; }

.page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 28px;
    flex-wrap: wrap;
}
.page-header h1 {
    font-family: var(--font-head);
    font-size: 1.8rem;
    font-weight: 700;
    display: flex;
    align-items: center;
    gap: 12px;
}
.page-header h1 i { color: var(--accent); }
.page-header .count {
    font-size: .85rem;
    color: var(--muted);
    background: var(--accent-lt);
    padding: 5px 12px;
    border-radius: 99px;
    font-weight: 600;
}
.back-link {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    font-size: .85rem;
    color: var(--muted);
    transition: color .15s;
}
.back-link:hover { color: var(--accent); }

/* ── Product grid (idéntico a cliente.php) ───────────────── */
.product-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 16px;
}

.prod-card {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    overflow: hidden;
    transition: box-shadow .3s, transform .3s, border-color .3s;
    position: relative;
    display: flex;
    flex-direction: column;
    cursor: pointer;
}
.prod-card:hover {
    box-shadow: var(--shadow-lg), 0 0 0 1px rgba(201,168,76,.35), 0 0 40px rgba(201,168,76,.12);
    transform: translateY(-5px);
    border-color: rgba(201,168,76,.35);
}

.prod-img-wrap {
    position: relative;
    width: 100%;
    padding-top: 100%;
    background: var(--bg);
    overflow: hidden;
}
.prod-img-wrap img {
    position: absolute; inset: 0;
    width: 100%; height: 100%;
    object-fit: cover;
    transition: transform .35s;
}
.prod-card:hover .prod-img-wrap img { transform: scale(1.04); }
.prod-img-wrap .no-img {
    position: absolute; inset: 0;
    display: flex; align-items: center; justify-content: center;
    font-size: 2.5rem;
    color: var(--border);
}

.badge-oferta {
    position: absolute; top: 10px; left: 10px;
    background: var(--accent);
    color: #fff;
    font-size: .68rem;
    font-weight: 700;
    padding: 3px 8px;
    border-radius: 99px;
    letter-spacing: .03em;
}

.fav-btn {
    position: absolute; top: 8px; right: 8px;
    width: 32px; height: 32px;
    border-radius: 50%;
    background: rgba(13,13,20,.55);
    backdrop-filter: blur(6px);
    border: 1px solid rgba(255,255,255,.12);
    display: flex; align-items: center; justify-content: center;
    font-size: .85rem;
    color: #fff;
    transition: color .2s, background .2s, transform .2s;
}
.fav-btn:hover { transform: scale(1.1); }
.fav-btn:hover, .fav-btn.active { color: #ff4d6d; background: rgba(13,13,20,.8); }
.fav-btn.active i { animation: heartPop .35s ease; }
@keyframes heartPop {
    0%   { transform: scale(1); }
    40%  { transform: scale(1.35); }
    100% { transform: scale(1); }
}

.prod-body { padding: 12px 14px 14px; flex: 1; display: flex; flex-direction: column; gap: 6px; }
.prod-store { display: flex; align-items: center; gap: 6px; font-size: .72rem; color: var(--muted); }
.prod-store-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.prod-name {
    font-size: .9rem; font-weight: 600; line-height: 1.3; color: var(--text);
    display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.prod-rating { display: flex; align-items: center; gap: 4px; font-size: .75rem; color: var(--gold); }
.prod-rating span { color: var(--muted); }
.prod-price-row { display: flex; align-items: baseline; gap: 8px; margin-top: auto; }
.prod-price { font-size: 1.05rem; font-weight: 700; color: var(--text); }
.prod-price.oferta { color: var(--accent); }
.prod-price-old { font-size: .8rem; color: var(--muted); text-decoration: line-through; }
.prod-footer { padding: 0 14px 14px; }
.btn-cart {
    width: 100%;
    padding: 9px 0;
    background: linear-gradient(135deg, var(--gold), var(--gold-dk));
    color: #0d0d14;
    border: none;
    border-radius: 8px;
    font-size: .83rem;
    font-weight: 700;
    display: flex; align-items: center; justify-content: center; gap: 7px;
    transition: filter .2s, transform .2s, box-shadow .2s;
}
.btn-cart:hover { filter: brightness(1.08); transform: translateY(-1px); box-shadow: 0 8px 20px rgba(201,168,76,.3); }
.btn-cart:active { transform: translateY(0); filter: brightness(.95); }

/* ── Empty state ──────────────────────────────────────────── */
.empty {
    text-align: center;
    padding: 90px 20px;
    color: var(--muted);
}
.empty i { font-size: 3.6rem; margin-bottom: 16px; opacity: .3; color: var(--accent); }
.empty h3 { font-family: var(--font-head); font-size: 1.3rem; font-weight: 700; color: var(--text); margin-bottom: 8px; }
.empty p { font-size: .9rem; margin-bottom: 22px; }
.empty .btn-explore {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 12px 24px;
    background: var(--accent);
    color: #fff;
    border-radius: 99px;
    font-weight: 700;
    font-size: .85rem;
    transition: filter .2s, transform .2s;
}
.empty .btn-explore:hover { filter: brightness(1.08); transform: translateY(-1px); }

/* ── Toasts ───────────────────────────────────────────────── */
#toasts { position: fixed; bottom: 24px; right: 24px; z-index: 999; display: flex; flex-direction: column; gap: 10px; }
.toast {
    background: var(--text);
    color: #fff;
    padding: 12px 18px;
    border-radius: 10px;
    font-size: .85rem;
    font-weight: 500;
    box-shadow: var(--shadow-lg);
    display: flex; align-items: center; gap: 8px;
    animation: toastIn .25s ease;
}
.toast.ok i  { color: #6ee7a8; }
.toast.err i { color: #ff8a80; }
@keyframes toastIn { from { opacity:0; transform: translateY(8px); } to { opacity:1; transform: translateY(0); } }

@media (max-width: 640px) {
    .navbar { padding: 0 16px; gap: 12px; }
    .page-wrap { padding: 24px 16px 50px; }
}
</style>
</head>
<body>

<nav class="navbar">
    <a href="cliente.php" class="nav-logo">Caboré</a>

    <div class="nav-actions">
        <button class="nav-btn" title="Favoritos" style="background:var(--accent-lt);color:var(--accent);border-color:var(--accent)">
            <i class="fas fa-heart"></i>
        </button>
        <button class="nav-btn" title="Carrito" onclick="window.location='cliente.php'">
            <i class="fas fa-shopping-bag"></i>
            <?php if ($carrito_count > 0): ?>
            <span class="cart-badge"><?= $carrito_count ?></span>
            <?php endif; ?>
        </button>
        <div class="nav-user">
            <div class="nav-avatar" title="Mi cuenta">
                <?= strtoupper(substr($usuario['nombre'], 0, 1)) ?>
            </div>
            <div class="nav-dropdown">
                <a href="perfil.php"><i class="far fa-user"></i> <?= htmlspecialchars($usuario['nombre']) ?></a>
                <a href="perfil.php"><i class="fas fa-map-marker-alt"></i> Mi perfil y dirección</a>
                <a href="mis_pedidos.php"><i class="fas fa-box"></i> Mis pedidos</a>
                <a href="favoritos.php"><i class="fas fa-heart"></i> Favoritos</a>
                <hr>
                <a href="logout.php" class="danger"><i class="fas fa-sign-out-alt"></i> Cerrar sesión</a>
            </div>
        </div>
    </div>
</nav>

<div class="page-wrap">

    <a href="cliente.php" class="back-link" style="margin-bottom:14px;display:inline-flex">
        <i class="fas fa-arrow-left"></i> Seguir comprando
    </a>

    <div class="page-header">
        <h1><i class="fas fa-heart"></i> Mis favoritos</h1>
        <?php if (count($productos)): ?>
        <span class="count"><?= count($productos) ?> producto<?= count($productos) != 1 ? 's' : '' ?> guardado<?= count($productos) != 1 ? 's' : '' ?></span>
        <?php endif; ?>
    </div>

    <?php if (count($productos)): ?>
    <div class="product-grid" id="fav-grid">
        <?php foreach ($productos as $p): ?>
        <?php
            $precio_final = $p['precio_oferta'] && $p['precio_oferta'] < $p['precio']
                ? $p['precio_oferta'] : $p['precio'];
            $tiene_oferta = $p['precio_oferta'] && $p['precio_oferta'] < $p['precio'];
            $descuento = $tiene_oferta ? round((1 - $p['precio_oferta'] / $p['precio']) * 100) : 0;
        ?>
        <div class="prod-card" id="fav-card-<?= $p['id'] ?>" onclick="window.location='cliente.php?producto=<?= $p['id'] ?>'">
            <div class="prod-img-wrap">
                <?php if ($p['imagen']): ?>
                    <img src="img/productos/<?= htmlspecialchars($p['imagen']) ?>" alt="<?= htmlspecialchars($p['nombre']) ?>" loading="lazy">
                <?php else: ?>
                    <div class="no-img"><i class="fas fa-box-open"></i></div>
                <?php endif; ?>
                <?php if ($tiene_oferta): ?>
                    <span class="badge-oferta">-<?= $descuento ?>%</span>
                <?php endif; ?>
                <button class="fav-btn active"
                        onclick="event.stopPropagation(); quitarFavorito(<?= $p['id'] ?>)">
                    <i class="fas fa-heart"></i>
                </button>
            </div>
            <div class="prod-body">
                <div class="prod-store">
                    <span class="prod-store-dot" style="background:<?= htmlspecialchars($p['tienda_color'] ?: '#888') ?>"></span>
                    <?= htmlspecialchars($p['tienda_nombre']) ?>
                </div>
                <div class="prod-name"><?= htmlspecialchars($p['nombre']) ?></div>
                <?php if ($p['rating'] > 0): ?>
                <div class="prod-rating">
                    <?php for ($i=1; $i<=5; $i++): ?>
                        <i class="<?= $i <= round($p['rating']) ? 'fas' : 'far' ?> fa-star"></i>
                    <?php endfor; ?>
                    <span>(<?= $p['total_resenas'] ?>)</span>
                </div>
                <?php endif; ?>
                <div class="prod-price-row">
                    <span class="prod-price <?= $tiene_oferta ? 'oferta' : '' ?>">
                        $<?= number_format($precio_final, 0, ',', '.') ?>
                    </span>
                    <?php if ($tiene_oferta): ?>
                    <span class="prod-price-old">$<?= number_format($p['precio'], 0, ',', '.') ?></span>
                    <?php endif; ?>
                </div>
            </div>
            <div class="prod-footer">
                <?php if ($p['activo'] && $p['stock'] > 0): ?>
                <button class="btn-cart"
                        onclick="event.stopPropagation(); agregarAlCarrito(<?= $p['id'] ?>, '<?= htmlspecialchars(addslashes($p['nombre'])) ?>')">
                    <i class="fas fa-bag-shopping"></i> Agregar
                </button>
                <?php else: ?>
                <button class="btn-cart" style="background:var(--border);color:var(--muted);cursor:not-allowed" disabled>
                    Sin stock
                </button>
                <?php endif; ?>
            </div>
        </div>
        <?php endforeach; ?>
    </div>
    <?php else: ?>
    <div class="empty">
        <i class="far fa-heart"></i>
        <h3>Aún no tienes favoritos</h3>
        <p>Toca el corazón de cualquier producto mientras exploras para guardarlo aquí.</p>
        <a href="cliente.php" class="btn-explore"><i class="fas fa-store"></i> Explorar tiendas</a>
    </div>
    <?php endif; ?>

</div>

<div id="toasts"></div>

<script>
function toast(msg, tipo = 'ok') {
    const div = document.createElement('div');
    div.className = 'toast ' + tipo;
    div.innerHTML = msg;
    document.getElementById('toasts').appendChild(div);
    setTimeout(() => div.remove(), 3000);
}

// Quitar de favoritos (usa el mismo endpoint toggle_favorito.php de cliente.php)
function quitarFavorito(prodId) {
    fetch('toggle_favorito.php', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'producto_id=' + encodeURIComponent(prodId)
    })
    .then(r => r.json())
    .then(data => {
        if (data.ok) {
            const card = document.getElementById('fav-card-' + prodId);
            if (card) {
                card.style.transition = 'opacity .25s, transform .25s';
                card.style.opacity = '0';
                card.style.transform = 'scale(.92)';
                setTimeout(() => {
                    card.remove();
                    const grid = document.getElementById('fav-grid');
                    if (grid && grid.children.length === 0) {
                        location.reload(); // muestra el empty state con el diseño correcto
                    }
                }, 250);
            }
            toast('<i class="fas fa-heart-crack"></i> Eliminado de favoritos', 'ok');
        } else {
            toast(data.msg || 'No se pudo actualizar', 'err');
        }
    })
    .catch(() => toast('Error de conexión', 'err'));
}

// Agregar al carrito (mismo carrito_ajax.php real de cliente.php)
function agregarAlCarrito(prodId, nombre) {
    fetch('carrito_ajax.php', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'accion=agregar&id=' + encodeURIComponent(prodId)
    })
    .then(r => r.json())
    .then(data => {
        if (data.ok) {
            toast(`<i class="fas fa-check-circle"></i> "${nombre.substring(0,28)}" agregado al carrito`, 'ok');
            const badge = document.querySelector('.cart-badge');
            if (badge) { badge.textContent = data.count; badge.style.display = 'flex'; }
        } else {
            toast(data.error || 'No se pudo agregar', 'err');
        }
    })
    .catch(() => toast('Error de conexión', 'err'));
}
</script>
</body>
</html>