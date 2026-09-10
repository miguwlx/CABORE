<?php
session_start();
require_once 'conexion.php';

// Verificar sesión y rol
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

// Obtener datos del usuario
$stmt = $pdo->prepare("SELECT * FROM usuarios WHERE id = ?");
$stmt->execute([$usuario_id]);
$usuario = $stmt->fetch();

// Obtener categorías
$categorias = $pdo->query("SELECT * FROM categorias ORDER BY nombre")->fetchAll();

// Parámetros de filtro
$busqueda    = trim($_GET['q'] ?? '');
$cat_id      = intval($_GET['categoria'] ?? 0);
$orden       = $_GET['orden'] ?? 'reciente';
$solo_oferta = isset($_GET['oferta']);

// Construir query de productos
$where  = ["p.activo = 1", "p.stock > 0"];
$params = [];

if ($busqueda) {
    $where[]  = "(p.nombre LIKE ? OR p.descripcion LIKE ? OR t.nombre LIKE ?)";
    $like     = "%$busqueda%";
    $params[] = $like;
    $params[] = $like;
    $params[] = $like;
}

if ($cat_id) {
    $where[]  = "p.categoria_id = ?";
    $params[] = $cat_id;
}

if ($solo_oferta) {
    $where[] = "p.precio_oferta IS NOT NULL AND p.precio_oferta < p.precio";
}

$order_sql = match($orden) {
    'precio_asc'  => "p.precio ASC",
    'precio_desc' => "p.precio DESC",
    'nombre'      => "p.nombre ASC",
    default       => "p.id DESC",
};

$sql = "
    SELECT p.*, t.nombre AS tienda_nombre, t.color AS tienda_color, t.logo AS tienda_logo,
           t.whatsapp AS tienda_wa, t.id AS tienda_id,
           c.nombre AS categoria_nombre,
           COALESCE(AVG(r.calificacion), 0) AS rating,
           COUNT(r.id) AS total_resenas
    FROM productos p
    JOIN tiendas t ON p.tienda_id = t.id
    LEFT JOIN categorias c ON p.categoria_id = c.id
    LEFT JOIN resenas r ON r.producto_id = p.id
    WHERE " . implode(' AND ', $where) . "
    GROUP BY p.id
    ORDER BY $order_sql
    LIMIT 60
";

$stmt = $pdo->prepare($sql);
$stmt->execute($params);
$productos = $stmt->fetchAll();

// Productos destacados (sugerencias)
$sugerencias = $pdo->query("
    SELECT p.*, t.nombre AS tienda_nombre, t.color AS tienda_color, t.logo AS tienda_logo,
           COALESCE(AVG(r.calificacion), 0) AS rating
    FROM productos p
    JOIN tiendas t ON p.tienda_id = t.id
    LEFT JOIN resenas r ON r.producto_id = p.id
    WHERE p.activo = 1 AND p.destacado = 1 AND p.stock > 0
    GROUP BY p.id
    ORDER BY RAND()
    LIMIT 8
")->fetchAll();

// Favoritos del usuario
$favs_raw = $pdo->prepare("SELECT producto_id FROM favoritos WHERE usuario_id = ?");
$favs_raw->execute([$usuario_id]);
$favoritos = array_column($favs_raw->fetchAll(), 'producto_id');

// Contar ítems en carrito (session, atado al usuario — ver guard arriba)
$carrito_count = array_sum($_SESSION['carrito']);

// Mensaje flash
$flash = $_SESSION['flash'] ?? null;
unset($_SESSION['flash']);
?>
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Caboré — Explorar</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Playfair+Display:wght@600;700;900&family=DM+Sans:wght@300;400;500;600;700&display=swap" rel="stylesheet">
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css">
<style>
/* ── Reset & tokens ─────────────────────────────────────── */
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

:root {
    --bg:        #faf7f0;
    --surface:   #ffffff;
    --border:    rgba(184,146,60,0.28);
    --text:      #1c1810;
    --muted:     rgba(28,24,16,0.56);
    --accent:    #e2603f;       /* ámbar/coral — ofertas, favoritos, checkout */
    --accent-lt: rgba(226,96,63,0.12);
    --green:     #0f9d63;
    --green-lt:  rgba(15,157,99,0.12);
    --gold:      #b8923c;       /* dorado Caboré (oscurecido para contraste en claro) */
    --gold-dk:   #8c6c28;
    --radius:    14px;
    --shadow:    0 4px 16px rgba(28,24,16,.08);
    --shadow-lg: 0 24px 60px rgba(28,24,16,.14);
    --font-head: 'Playfair Display', Georgia, serif;
    --font-body: 'DM Sans', system-ui, sans-serif;
    --nav-h:     64px;
    --sidebar-w: 260px;
}

body {
    font-family: var(--font-body);
    background: var(--bg);
    color: var(--text);
    min-height: 100vh;
    position: relative;
}

/* ── Ambiente de marca: grid + halos dorados ─────────────── */
.bg-grid {
    position: fixed; inset: 0; z-index: 0; pointer-events: none;
    background-image:
        linear-gradient(rgba(184,146,60,0.07) 1px, transparent 1px),
        linear-gradient(90deg, rgba(184,146,60,0.07) 1px, transparent 1px);
    background-size: 60px 60px;
}
.bg-glow {
    position: fixed; z-index: 0; pointer-events: none;
    width: 520px; height: 520px; border-radius: 50%;
    filter: blur(130px); opacity: 0.16;
}
.bg-glow.a { background: #d4af5a; top: -120px; right: -120px; }
.bg-glow.b { background: #e2603f; bottom: -140px; left: -140px; opacity: .10; }

.navbar, .layout, .cart-overlay, .cart-drawer, .modal-overlay, .toast-container {
    position: relative; z-index: 1;
}

::-webkit-scrollbar { width: 10px; height: 10px; }
::-webkit-scrollbar-track { background: var(--bg); }
::-webkit-scrollbar-thumb { background: rgba(184,146,60,0.30); border-radius: 99px; }
::-webkit-scrollbar-thumb:hover { background: rgba(184,146,60,0.5); }

a { color: inherit; text-decoration: none; }
img { display: block; max-width: 100%; }
button { cursor: pointer; font-family: var(--font-body); }

/* ── Navbar ─────────────────────────────────────────────── */
.navbar {
    position: sticky;
    top: 0;
    z-index: 100;
    height: var(--nav-h);
    background: var(--surface);
    border-bottom: 1px solid var(--border);
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 0 24px;
}

.nav-logo {
    font-family: var(--font-head);
    font-size: 1.6rem;
    font-weight: 900;
    color: var(--gold);
    letter-spacing: -.5px;
    white-space: nowrap;
}

.nav-search {
    flex: 1;
    max-width: 520px;
    position: relative;
}

.nav-search input {
    width: 100%;
    height: 40px;
    border: 1.5px solid var(--border);
    border-radius: 99px;
    padding: 0 16px 0 40px;
    font-size: .9rem;
    font-family: var(--font-body);
    background: var(--bg);
    color: var(--text);
    transition: border-color .2s;
}
.nav-search input:focus {
    outline: none;
    border-color: var(--accent);
    background: var(--surface);
}
.nav-search i {
    position: absolute;
    left: 14px;
    top: 50%;
    transform: translateY(-50%);
    color: var(--muted);
    font-size: .85rem;
}

.nav-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-left: auto;
}

.nav-btn {
    position: relative;
    width: 40px;
    height: 40px;
    border-radius: 50%;
    border: 1.5px solid var(--border);
    background: transparent;
    color: var(--text);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: .95rem;
    transition: background .15s, border-color .15s;
}
.nav-btn:hover { background: var(--bg); border-color: var(--accent); color: var(--accent); }

.cart-badge {
    position: absolute;
    top: -4px;
    right: -4px;
    width: 18px;
    height: 18px;
    background: var(--accent);
    color: #fff;
    font-size: .65rem;
    font-weight: 700;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
}

.nav-avatar {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    background: var(--accent-lt);
    color: var(--accent);
    font-weight: 700;
    font-size: .85rem;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 2px solid var(--accent);
}

.nav-user {
    position: relative;
}
.nav-user:hover .nav-dropdown { display: block; }

.nav-dropdown {
    display: none;
    position: absolute;
    top: calc(100% + 8px);
    right: 0;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    box-shadow: var(--shadow-lg);
    min-width: 180px;
    overflow: hidden;
    z-index: 200;
}
.nav-dropdown a {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 11px 16px;
    font-size: .875rem;
    color: var(--text);
    transition: background .15s;
}
.nav-dropdown a:hover { background: var(--bg); }
.nav-dropdown a.danger { color: #b91c1c; }
.nav-dropdown hr { border: none; border-top: 1px solid var(--border); margin: 4px 0; }

/* ── Layout ─────────────────────────────────────────────── */
.layout {
    display: flex;
    min-height: calc(100vh - var(--nav-h));
}

/* ── Sidebar ─────────────────────────────────────────────── */
.sidebar {
    width: var(--sidebar-w);
    flex-shrink: 0;
    background: var(--surface);
    border-right: 1px solid var(--border);
    padding: 28px 20px;
    position: sticky;
    top: var(--nav-h);
    height: calc(100vh - var(--nav-h));
    overflow-y: auto;
}

.sidebar-title {
    font-size: .7rem;
    font-weight: 700;
    letter-spacing: .1em;
    text-transform: uppercase;
    color: var(--muted);
    margin-bottom: 12px;
}

.filter-group { margin-bottom: 28px; }

.cat-list { list-style: none; }
.cat-list li a {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 10px;
    border-radius: 8px;
    font-size: .875rem;
    color: var(--text);
    transition: background .15s;
}
.cat-list li a:hover { background: var(--bg); }
.cat-list li a.active {
    background: var(--accent-lt);
    color: var(--accent);
    font-weight: 600;
}
.cat-list li a span {
    font-size: .75rem;
    color: var(--muted);
    background: var(--bg);
    padding: 1px 7px;
    border-radius: 99px;
}

.filter-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 9px 10px;
    border-radius: 8px;
    font-size: .875rem;
    cursor: pointer;
    transition: background .15s;
}
.filter-row:hover { background: var(--bg); }
.filter-row.active { background: var(--green-lt); color: var(--green); font-weight: 600; }
.filter-row input { accent-color: var(--green); }

.order-select {
    width: 100%;
    padding: 9px 12px;
    border: 1.5px solid var(--border);
    border-radius: 8px;
    font-size: .875rem;
    font-family: var(--font-body);
    background: var(--bg);
    color: var(--text);
}
.order-select:focus { outline: none; border-color: var(--accent); }
.order-select option { background: #1a1a2e; color: var(--text); }

/* ── Main ─────────────────────────────────────────────────── */
.main {
    flex: 1;
    padding: 28px 24px;
    overflow-x: hidden;
}

/* ── Flash ───────────────────────────────────────────────── */
.flash {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 12px 18px;
    border-radius: var(--radius);
    margin-bottom: 20px;
    font-size: .875rem;
    font-weight: 500;
}
.flash.ok  { background: rgba(15,157,99,.10); border: 1px solid rgba(15,157,99,.28); color: #0c7a4d; }
.flash.err { background: rgba(220,38,38,.10);  border: 1px solid rgba(220,38,38,.25);  color: #b91c1c; }

/* ── Section header ──────────────────────────────────────── */
.section-header {
    display: flex;
    align-items: baseline;
    gap: 10px;
    margin-bottom: 18px;
}
.section-header h2 {
    font-family: var(--font-head);
    font-size: 1.5rem;
    font-weight: 700;
    background: linear-gradient(135deg, var(--text), var(--gold));
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
}
.section-header span {
    font-size: .8rem;
    color: var(--muted);
}

/* ── Highlights (carrusel de sugerencias) ────────────────── */
.highlights {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
    gap: 14px;
    margin-bottom: 36px;
}

/* ── Product grid ────────────────────────────────────────── */
.product-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 16px;
}

/* ── Product card ────────────────────────────────────────── */
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
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform .35s;
}
.prod-card:hover .prod-img-wrap img { transform: scale(1.04); }

.prod-img-wrap .no-img {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 2.5rem;
    color: var(--border);
}

.badge-oferta {
    position: absolute;
    top: 10px;
    left: 10px;
    background: var(--accent);
    color: #fff;
    font-size: .68rem;
    font-weight: 700;
    padding: 3px 8px;
    border-radius: 99px;
    letter-spacing: .03em;
}

.fav-btn {
    position: absolute;
    top: 8px;
    right: 8px;
    width: 32px;
    height: 32px;
    border-radius: 50%;
    background: rgba(13,13,20,.55);
    backdrop-filter: blur(6px);
    border: 1px solid rgba(255,255,255,.12);
    display: flex;
    align-items: center;
    justify-content: center;
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

.prod-body {
    padding: 12px 14px 14px;
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.prod-store {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: .72rem;
    color: var(--muted);
}
.prod-store-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;
}

.prod-name {
    font-size: .9rem;
    font-weight: 600;
    line-height: 1.3;
    color: var(--text);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

.prod-rating {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: .75rem;
    color: var(--gold);
}
.prod-rating span { color: var(--muted); }

.prod-price-row {
    display: flex;
    align-items: baseline;
    gap: 8px;
    margin-top: auto;
}
.prod-price {
    font-size: 1.05rem;
    font-weight: 700;
    color: var(--text);
}
.prod-price.oferta { color: var(--accent); }
.prod-price-old {
    font-size: .8rem;
    color: var(--muted);
    text-decoration: line-through;
}

.prod-footer {
    padding: 0 14px 14px;
}
.btn-cart {
    width: 100%;
    padding: 9px 0;
    background: linear-gradient(135deg, var(--gold), var(--gold-dk));
    color: #0d0d14;
    border: none;
    border-radius: 8px;
    font-size: .83rem;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 7px;
    transition: filter .2s, transform .2s, box-shadow .2s;
}
.btn-cart:hover { filter: brightness(1.08); transform: translateY(-1px); box-shadow: 0 8px 20px rgba(201,168,76,.3); }
.btn-cart:active { transform: translateY(0); filter: brightness(.95); }

/* ── Mini badge destacado ────────────────────────────────── */
.badge-destacado {
    position: absolute;
    top: 10px;
    left: 10px;
    background: linear-gradient(135deg, var(--gold), var(--gold-dk));
    color: #0d0d14;
    font-size: .65rem;
    font-weight: 700;
    padding: 3px 7px;
    border-radius: 99px;
}

/* ── Empty state ─────────────────────────────────────────── */
.empty {
    text-align: center;
    padding: 60px 20px;
    color: var(--muted);
}
.empty i { font-size: 3rem; margin-bottom: 14px; opacity: .35; }
.empty h3 { font-size: 1.1rem; font-weight: 600; color: var(--text); margin-bottom: 6px; }
.empty p { font-size: .875rem; }

/* ── Carrito drawer ──────────────────────────────────────── */
.cart-overlay {
    display: none;
    position: fixed;
    inset: 0;
    background: rgba(0,0,0,.45);
    z-index: 300;
}
.cart-overlay.open { display: block; }

.cart-drawer {
    position: fixed;
    top: 0;
    right: 0;
    height: 100%;
    width: 380px;
    max-width: 95vw;
    background: var(--surface);
    box-shadow: var(--shadow-lg);
    z-index: 301;
    display: flex;
    flex-direction: column;
    transform: translateX(100%);
    transition: transform .3s cubic-bezier(.4,0,.2,1);
}
.cart-drawer.open { transform: translateX(0); }

.cart-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 20px 22px;
    border-bottom: 1px solid var(--border);
}
.cart-head h3 { font-size: 1.1rem; font-weight: 700; }
.cart-head button {
    background: none;
    border: none;
    font-size: 1.2rem;
    color: var(--muted);
    padding: 4px;
}

.cart-body { flex: 1; overflow-y: auto; padding: 16px; }

.cart-item {
    display: flex;
    gap: 12px;
    padding: 14px 0;
    border-bottom: 1px solid var(--border);
}
.cart-item img {
    width: 64px;
    height: 64px;
    object-fit: cover;
    border-radius: 8px;
    background: var(--bg);
    flex-shrink: 0;
}
.cart-item-info { flex: 1; }
.cart-item-name { font-size: .875rem; font-weight: 600; margin-bottom: 4px; }
.cart-item-store { font-size: .75rem; color: var(--muted); margin-bottom: 8px; }
.cart-item-controls {
    display: flex;
    align-items: center;
    gap: 10px;
}
.qty-btn {
    width: 26px;
    height: 26px;
    border-radius: 50%;
    border: 1.5px solid var(--border);
    background: transparent;
    font-size: .9rem;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: border-color .15s;
}
.qty-btn:hover { border-color: var(--accent); color: var(--accent); }
.qty-val { font-size: .875rem; font-weight: 600; min-width: 20px; text-align: center; }
.cart-item-price { font-size: .875rem; font-weight: 700; align-self: center; }
.cart-item-del { margin-left: auto; background: none; border: none; color: var(--muted); font-size: .85rem; }
.cart-item-del:hover { color: #b91c1c; }

.cart-empty {
    text-align: center;
    padding: 60px 20px;
    color: var(--muted);
}
.cart-empty i { font-size: 2.5rem; opacity: .35; margin-bottom: 12px; }
.cart-empty p { font-size: .875rem; }

.cart-foot {
    padding: 18px 22px;
    border-top: 1px solid var(--border);
}
.cart-total-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 14px;
}
.cart-total-label { font-size: .875rem; color: var(--muted); }
.cart-total-val { font-size: 1.25rem; font-weight: 700; }
.btn-checkout {
    width: 100%;
    padding: 13px;
    background: linear-gradient(135deg, var(--accent), #ff8c5a);
    color: #fff;
    border: none;
    border-radius: var(--radius);
    font-size: .95rem;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    transition: filter .2s, transform .2s, box-shadow .2s;
}
.btn-checkout:hover { filter: brightness(1.08); transform: translateY(-1px); box-shadow: 0 10px 24px rgba(255,107,74,.3); }

/* ── Modal producto ──────────────────────────────────────── */
.modal-overlay {
    display: none;
    position: fixed;
    inset: 0;
    background: rgba(0,0,0,.5);
    z-index: 400;
    align-items: center;
    justify-content: center;
    padding: 20px;
}
.modal-overlay.open { display: flex; }

.modal {
    background: var(--surface);
    border-radius: 16px;
    max-width: 660px;
    width: 100%;
    max-height: 90vh;
    overflow-y: auto;
    box-shadow: var(--shadow-lg);
}
.modal-img {
    width: 100%;
    height: 280px;
    object-fit: cover;
    border-radius: 16px 16px 0 0;
    background: var(--bg);
}
.modal-body { padding: 24px 28px 28px; }
.modal-cat { font-size: .75rem; color: var(--muted); text-transform: uppercase; letter-spacing: .08em; margin-bottom: 8px; }
.modal-name { font-family: var(--font-head); font-size: 1.5rem; font-weight: 600; line-height: 1.2; margin-bottom: 8px; }
.modal-store { font-size: .85rem; color: var(--muted); margin-bottom: 12px; display: flex; align-items: center; gap: 6px; }
.modal-desc { font-size: .9rem; line-height: 1.6; color: var(--muted); margin-bottom: 16px; }
.modal-price-row { display: flex; align-items: baseline; gap: 12px; margin-bottom: 20px; }
.modal-price { font-size: 1.6rem; font-weight: 700; }
.modal-price.oferta { color: var(--accent); }
.modal-price-old { font-size: 1rem; text-decoration: line-through; color: var(--muted); }
.modal-stock { font-size: .8rem; color: var(--green); background: var(--green-lt); padding: 3px 10px; border-radius: 99px; font-weight: 600; }
.modal-actions { display: flex; gap: 12px; flex-wrap: wrap; }
.modal-btn-cart {
    flex: 1;
    min-width: 160px;
    padding: 12px 20px;
    background: linear-gradient(135deg, var(--gold), var(--gold-dk));
    color: #0d0d14;
    border: none;
    border-radius: var(--radius);
    font-size: .9rem;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    transition: filter .2s, transform .2s;
}
.modal-btn-cart:hover { filter: brightness(1.08); transform: translateY(-1px); }
.modal-btn-wa {
    padding: 12px 20px;
    background: #25D366;
    color: #fff;
    border: none;
    border-radius: var(--radius);
    font-size: .9rem;
    font-weight: 700;
    display: flex;
    align-items: center;
    gap: 8px;
    transition: background .2s;
}
.modal-btn-wa:hover { background: #1da851; }
.modal-close {
    position: absolute;
    top: 16px;
    right: 16px;
    width: 34px;
    height: 34px;
    border-radius: 50%;
    background: rgba(0,0,0,.3);
    color: #fff;
    border: none;
    font-size: .9rem;
    display: flex;
    align-items: center;
    justify-content: center;
}
.modal { position: relative; }

/* ── Toast ───────────────────────────────────────────────── */
.toast-container {
    position: fixed;
    bottom: 24px;
    right: 24px;
    z-index: 500;
    display: flex;
    flex-direction: column;
    gap: 10px;
}
.toast {
    background: var(--surface);
    color: var(--text);
    border: 1px solid var(--border);
    padding: 12px 18px;
    border-radius: var(--radius);
    font-size: .875rem;
    font-weight: 500;
    box-shadow: var(--shadow-lg);
    display: flex;
    align-items: center;
    gap: 10px;
    animation: slideIn .3s ease;
}
.toast.ok { border-color: rgba(15,157,99,.40); color: #0c7a4d; }
.toast i { font-size: 1rem; color: var(--gold); }
.toast.ok i { color: #0c7a4d; }

@keyframes slideIn {
    from { transform: translateX(40px); opacity: 0; }
    to   { transform: translateX(0);    opacity: 1; }
}

/* ── Responsive ──────────────────────────────────────────── */
@media (max-width: 900px) {
    .sidebar { display: none; }
    .product-grid { grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); }
    .highlights   { grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); }
}

@media (max-width: 540px) {
    .main { padding: 16px; }
    .product-grid { grid-template-columns: 1fr 1fr; gap: 10px; }
    .highlights   { grid-template-columns: 1fr 1fr; gap: 10px; }
    .navbar { padding: 0 14px; gap: 10px; }
    .nav-search { max-width: 100%; }
}
</style>
</head>
<body>

<div class="bg-grid"></div>
<div class="bg-glow a"></div>
<div class="bg-glow b"></div>

<!-- ── Navbar ──────────────────────────────────────────────── -->
<nav class="navbar">
    <a href="cliente.php" class="nav-logo">Caboré</a>

    <form class="nav-search" method="GET" action="cliente.php">
        <?php if ($cat_id):  ?><input type="hidden" name="categoria" value="<?= $cat_id ?>"><?php endif; ?>
        <?php if ($solo_oferta): ?><input type="hidden" name="oferta" value="1"><?php endif; ?>
        <i class="fas fa-search"></i>
        <input type="text" name="q" placeholder="Buscar productos o tiendas…" value="<?= htmlspecialchars($busqueda) ?>" autocomplete="off">
    </form>

    <div class="nav-actions">
        <button class="nav-btn" title="Favoritos" onclick="window.location='favoritos.php'">
            <i class="far fa-heart"></i>
        </button>
        <button class="nav-btn" title="Carrito" onclick="openCart()">
            <i class="fas fa-shopping-bag"></i>
            <?php if ($carrito_count > 0): ?>
            <span class="cart-badge" id="cart-badge"><?= $carrito_count ?></span>
            <?php else: ?>
            <span class="cart-badge" id="cart-badge" style="display:none">0</span>
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
                <a href="favoritos.php"><i class="far fa-heart"></i> Favoritos</a>
                <hr>
                <a href="logout.php" class="danger"><i class="fas fa-sign-out-alt"></i> Cerrar sesión</a>
            </div>
        </div>
    </div>
</nav>

<!-- ── Layout ──────────────────────────────────────────────── -->
<div class="layout">

    <!-- Sidebar filtros -->
    <aside class="sidebar">
        <form method="GET" action="cliente.php" id="filter-form">
            <?php if ($busqueda): ?><input type="hidden" name="q" value="<?= htmlspecialchars($busqueda) ?>"><?php endif; ?>

            <div class="filter-group">
                <div class="sidebar-title">Categorías</div>
                <ul class="cat-list">
                    <li>
                        <a href="<?= '?' . http_build_query(array_merge($_GET, ['categoria' => 0])) ?>"
                           class="<?= !$cat_id ? 'active' : '' ?>">
                            Todas <span></span>
                        </a>
                    </li>
                    <?php foreach ($categorias as $cat): ?>
                    <li>
                        <a href="<?= '?' . http_build_query(array_merge($_GET, ['categoria' => $cat['id']])) ?>"
                           class="<?= $cat_id == $cat['id'] ? 'active' : '' ?>">
                            <?= htmlspecialchars($cat['nombre']) ?>
                        </a>
                    </li>
                    <?php endforeach; ?>
                </ul>
            </div>

            <div class="filter-group">
                <div class="sidebar-title">Filtrar por</div>
                <label class="filter-row <?= $solo_oferta ? 'active' : '' ?>">
                    <span><i class="fas fa-tag" style="margin-right:6px"></i>Solo en oferta</span>
                    <input type="checkbox" name="oferta" value="1" <?= $solo_oferta ? 'checked' : '' ?> onchange="document.getElementById('filter-form').submit()">
                </label>
            </div>

            <div class="filter-group">
                <div class="sidebar-title">Ordenar por</div>
                <select class="order-select" name="orden" onchange="document.getElementById('filter-form').submit()">
                    <option value="reciente"    <?= $orden==='reciente'    ? 'selected':'' ?>>Más recientes</option>
                    <option value="precio_asc"  <?= $orden==='precio_asc'  ? 'selected':'' ?>>Precio: menor a mayor</option>
                    <option value="precio_desc" <?= $orden==='precio_desc' ? 'selected':'' ?>>Precio: mayor a menor</option>
                    <option value="nombre"      <?= $orden==='nombre'      ? 'selected':'' ?>>Nombre A–Z</option>
                </select>
            </div>
        </form>
    </aside>

    <!-- Main content -->
    <main class="main">

        <?php if ($flash): ?>
        <div class="flash <?= $flash['tipo'] ?>">
            <i class="fas <?= $flash['tipo']==='ok' ? 'fa-check-circle' : 'fa-exclamation-circle' ?>"></i>
            <?= htmlspecialchars($flash['msg']) ?>
        </div>
        <?php endif; ?>

        <!-- Sugerencias destacadas (solo cuando no hay búsqueda activa) -->
        <?php if (!$busqueda && !$cat_id && !$solo_oferta && count($sugerencias)): ?>
        <div class="section-header">
            <h2>✨ Destacados para ti</h2>
        </div>
        <div class="highlights">
            <?php foreach ($sugerencias as $s): ?>
            <?php
                $precio_s = $s['precio_oferta'] && $s['precio_oferta'] < $s['precio']
                    ? $s['precio_oferta'] : $s['precio'];
                $tiene_oferta_s = $s['precio_oferta'] && $s['precio_oferta'] < $s['precio'];
            ?>
            <div class="prod-card" onclick="openModal(<?= htmlspecialchars(json_encode($s), ENT_QUOTES) ?>)">
                <div class="prod-img-wrap">
                    <?php if ($s['imagen']): ?>
                        <img src="img/productos/<?= htmlspecialchars($s['imagen']) ?>" alt="<?= htmlspecialchars($s['nombre']) ?>" loading="lazy">
                    <?php else: ?>
                        <div class="no-img"><i class="fas fa-box-open"></i></div>
                    <?php endif; ?>
                    <span class="badge-destacado"><i class="fas fa-star"></i> Top</span>
                    <button class="fav-btn <?= in_array($s['id'], $favoritos) ? 'active' : '' ?>"
                            onclick="event.stopPropagation(); toggleFav(this, <?= $s['id'] ?>)">
                        <i class="<?= in_array($s['id'], $favoritos) ? 'fas' : 'far' ?> fa-heart"></i>
                    </button>
                </div>
                <div class="prod-body">
                    <div class="prod-store">
                        <span class="prod-store-dot" style="background:<?= htmlspecialchars($s['tienda_color'] ?: '#888') ?>"></span>
                        <?= htmlspecialchars($s['tienda_nombre']) ?>
                    </div>
                    <div class="prod-name"><?= htmlspecialchars($s['nombre']) ?></div>
                    <div class="prod-price-row">
                        <span class="prod-price <?= $tiene_oferta_s ? 'oferta' : '' ?>">
                            $<?= number_format($precio_s, 0, ',', '.') ?>
                        </span>
                        <?php if ($tiene_oferta_s): ?>
                        <span class="prod-price-old">$<?= number_format($s['precio'], 0, ',', '.') ?></span>
                        <?php endif; ?>
                    </div>
                </div>
                <div class="prod-footer">
                    <button class="btn-cart" onclick="event.stopPropagation(); addToCart(<?= $s['id'] ?>, '<?= htmlspecialchars(addslashes($s['nombre'])) ?>', <?= $precio_s ?>, '<?= htmlspecialchars($s['imagen'] ?? '') ?>', '<?= htmlspecialchars(addslashes($s['tienda_nombre'])) ?>')">
                        <i class="fas fa-bag-shopping"></i> Agregar
                    </button>
                </div>
            </div>
            <?php endforeach; ?>
        </div>
        <?php endif; ?>

        <!-- Catálogo principal -->
        <div class="section-header">
            <h2><?= $busqueda ? 'Resultados para "'.htmlspecialchars($busqueda).'"' : ($cat_id ? htmlspecialchars($categorias[array_search($cat_id, array_column($categorias,'id'))]['nombre'] ?? 'Categoría') : 'Todos los productos') ?></h2>
            <span><?= count($productos) ?> producto<?= count($productos)!=1?'s':'' ?></span>
        </div>

        <?php if (count($productos)): ?>
        <div class="product-grid">
            <?php foreach ($productos as $p): ?>
            <?php
                $precio_final = $p['precio_oferta'] && $p['precio_oferta'] < $p['precio']
                    ? $p['precio_oferta'] : $p['precio'];
                $tiene_oferta = $p['precio_oferta'] && $p['precio_oferta'] < $p['precio'];
                $descuento = $tiene_oferta
                    ? round((1 - $p['precio_oferta'] / $p['precio']) * 100)
                    : 0;
            ?>
            <div class="prod-card" onclick="openModal(<?= htmlspecialchars(json_encode($p), ENT_QUOTES) ?>)">
                <div class="prod-img-wrap">
                    <?php if ($p['imagen']): ?>
                        <img src="img/productos/<?= htmlspecialchars($p['imagen']) ?>" alt="<?= htmlspecialchars($p['nombre']) ?>" loading="lazy">
                    <?php else: ?>
                        <div class="no-img"><i class="fas fa-box-open"></i></div>
                    <?php endif; ?>
                    <?php if ($tiene_oferta): ?>
                        <span class="badge-oferta">-<?= $descuento ?>%</span>
                    <?php endif; ?>
                    <button class="fav-btn <?= in_array($p['id'], $favoritos) ? 'active' : '' ?>"
                            onclick="event.stopPropagation(); toggleFav(this, <?= $p['id'] ?>)">
                        <i class="<?= in_array($p['id'], $favoritos) ? 'fas' : 'far' ?> fa-heart"></i>
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
                    <button class="btn-cart"
                            onclick="event.stopPropagation(); addToCart(<?= $p['id'] ?>, '<?= htmlspecialchars(addslashes($p['nombre'])) ?>', <?= $precio_final ?>, '<?= htmlspecialchars($p['imagen'] ?? '') ?>', '<?= htmlspecialchars(addslashes($p['tienda_nombre'])) ?>')">
                        <i class="fas fa-bag-shopping"></i> Agregar
                    </button>
                </div>
            </div>
            <?php endforeach; ?>
        </div>

        <?php else: ?>
        <div class="empty">
            <i class="fas fa-search"></i>
            <h3>Sin resultados</h3>
            <p>Intenta con otra categoría o cambia los filtros.</p>
        </div>
        <?php endif; ?>
    </main>
</div>

<!-- ── Carrito drawer ──────────────────────────────────────── -->
<div class="cart-overlay" id="cart-overlay" onclick="closeCart()"></div>
<div class="cart-drawer" id="cart-drawer">
    <div class="cart-head">
        <h3><i class="fas fa-shopping-bag"></i> Mi carrito</h3>
        <button onclick="closeCart()"><i class="fas fa-times"></i></button>
    </div>
    <div class="cart-body" id="cart-body">
        <div class="cart-empty"><i class="fas fa-shopping-bag"></i><p>Tu carrito está vacío</p></div>
    </div>
    <div class="cart-foot">
        <div class="cart-total-row">
            <span class="cart-total-label">Total</span>
            <span class="cart-total-val" id="cart-total">$0</span>
        </div>
        <button class="btn-checkout" onclick="checkout()">
            <i class="fas fa-credit-card"></i> Finalizar compra
        </button>
    </div>
</div>

<!-- ── Modal producto ──────────────────────────────────────── -->
<div class="modal-overlay" id="modal-overlay" onclick="closeModal(event)">
    <div class="modal" id="modal-box">
        <button class="modal-close" onclick="closeModal()"><i class="fas fa-times"></i></button>
        <img id="modal-img" class="modal-img" src="" alt="">
        <div class="modal-body">
            <div class="modal-cat"  id="modal-cat"></div>
            <div class="modal-name" id="modal-name"></div>
            <div class="modal-store" id="modal-store"></div>
            <div class="modal-desc" id="modal-desc"></div>
            <div class="modal-price-row">
                <span class="modal-price" id="modal-price"></span>
                <span class="modal-price-old" id="modal-price-old" style="display:none"></span>
                <span class="modal-stock" id="modal-stock"></span>
            </div>
            <div class="modal-actions">
                <button class="modal-btn-cart" id="modal-btn-cart">
                    <i class="fas fa-bag-shopping"></i> Agregar al carrito
                </button>
                <a class="modal-btn-wa" id="modal-btn-wa" href="#" target="_blank">
                    <i class="fab fa-whatsapp"></i> Contactar
                </a>
            </div>
        </div>
    </div>
</div>

<!-- ── Toasts ──────────────────────────────────────────────── -->
<div class="toast-container" id="toasts"></div>

<script>
/* ──────────────────────────────────────────────
   CARRITO — 100% servidor (sesión por usuario)
   Antes el carrito vivía en localStorage del navegador,
   que se comparte entre TODAS las cuentas que inicien
   sesión en ese mismo navegador/equipo. Por eso se
   mezclaban los carritos de distintos usuarios.
   Ahora cada acción llama a carrito_ajax.php, que guarda
   el carrito en $_SESSION atado al usuario_id autenticado.
────────────────────────────────────────────── */
let cart = []; // caché local solo para pintar la UI, la verdad vive en el servidor

// Limpieza de una sola vez: si quedaba un carrito viejo en localStorage
// de una versión anterior del sitio, se elimina para que no confunda.
if (localStorage.getItem('cabore_cart') !== null) {
    localStorage.removeItem('cabore_cart');
}

async function carritoFetch(accion, extra = {}) {
    const body = new URLSearchParams({ accion, ...extra });
    const res = await fetch('carrito_ajax.php', { method: 'POST', body });
    const data = await res.json();
    if (data.ok) {
        cart = data.items;
        pintarCarrito(data);
        if (data.cambio) {
            toast('Ajustamos tu carrito: algún producto cambió de stock o ya no está disponible', 'err');
        }
    } else if (data.error) {
        toast(data.error, 'err');
    }
    return data;
}

function cargarCarrito() {
    carritoFetch('listar');
}

function addToCart(id, nombre) {
    carritoFetch('agregar', { id }).then(data => {
        if (data.ok) {
            toast(`<i class="fas fa-check-circle"></i> "${nombre.substring(0,28)}" agregado`, 'ok');
        }
    });
}

function removeFromCart(id) {
    carritoFetch('eliminar', { id });
}

function changeQty(id, delta) {
    carritoFetch(delta > 0 ? 'incrementar' : 'disminuir', { id });
}

function pintarCarrito(data) {
    const badge = document.getElementById('cart-badge');
    badge.textContent = data.count;
    badge.style.display = data.count > 0 ? 'flex' : 'none';

    document.getElementById('cart-total').textContent =
        '$' + data.total.toLocaleString('es-CO');

    const body = document.getElementById('cart-body');
    if (data.items.length === 0) {
        body.innerHTML = '<div class="cart-empty"><i class="fas fa-shopping-bag"></i><p>Tu carrito está vacío</p></div>';
        return;
    }

    body.innerHTML = data.items.map(item => `
        <div class="cart-item">
            ${item.imagen
                ? `<img src="img/productos/${item.imagen}" alt="${item.nombre}">`
                : `<img src="" alt="" style="background:var(--bg)">`}
            <div class="cart-item-info">
                <div class="cart-item-name">${item.nombre}</div>
                <div class="cart-item-store">${item.tienda}</div>
                <div class="cart-item-controls">
                    <button class="qty-btn" onclick="changeQty(${item.id}, -1)">−</button>
                    <span class="qty-val">${item.cantidad}</span>
                    <button class="qty-btn" onclick="changeQty(${item.id}, +1)">+</button>
                </div>
            </div>
            <span class="cart-item-price">$${(item.precio * item.cantidad).toLocaleString('es-CO')}</span>
            <button class="cart-item-del" onclick="removeFromCart(${item.id})"><i class="fas fa-trash"></i></button>
        </div>
    `).join('');
}

function openCart() {
    document.getElementById('cart-overlay').classList.add('open');
    document.getElementById('cart-drawer').classList.add('open');
    cargarCarrito();
}

function closeCart() {
    document.getElementById('cart-overlay').classList.remove('open');
    document.getElementById('cart-drawer').classList.remove('open');
}

function checkout() {
    if (cart.length === 0) { toast('Tu carrito está vacío'); return; }
    // El carrito real vive en la sesión del servidor; checkout.php lo lee de ahí.
    window.location.href = 'checkout.php';
}

/* ──────────────────────────────────────────────
   FAVORITOS (AJAX)
────────────────────────────────────────────── */
function toggleFav(btn, prodId) {
    const icon = btn.querySelector('i');
    const isActive = btn.classList.contains('active');

    fetch('toggle_favorito.php', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'producto_id=' + prodId
    })
    .then(r => r.json())
    .then(data => {
        if (data.ok) {
            btn.classList.toggle('active');
            icon.className = btn.classList.contains('active') ? 'fas fa-heart' : 'far fa-heart';
            toast(btn.classList.contains('active')
                ? '<i class="fas fa-heart"></i> Agregado a favoritos'
                : '<i class="far fa-heart"></i> Eliminado de favoritos');
        }
    })
    .catch(() => toast('Error al actualizar favoritos'));
}

/* ──────────────────────────────────────────────
   MODAL
────────────────────────────────────────────── */
function openModal(p) {
    const overlay = document.getElementById('modal-overlay');
    const imgEl   = document.getElementById('modal-img');

    if (p.imagen) {
        imgEl.src = 'img/productos/' + p.imagen;
        imgEl.style.display = 'block';
    } else {
        imgEl.style.display = 'none';
    }

    document.getElementById('modal-cat').textContent  = p.categoria_nombre || '';
    document.getElementById('modal-name').textContent = p.nombre;
    document.getElementById('modal-store').innerHTML  =
        `<i class="fas fa-store"></i> ${p.tienda_nombre}`;
    document.getElementById('modal-desc').textContent =
        p.descripcion || 'Sin descripción disponible.';

    const precioFinal = (p.precio_oferta && p.precio_oferta < p.precio)
        ? p.precio_oferta : p.precio;
    const tieneOferta = p.precio_oferta && p.precio_oferta < p.precio;

    const priceEl = document.getElementById('modal-price');
    priceEl.textContent = '$' + parseFloat(precioFinal).toLocaleString('es-CO');
    priceEl.className = 'modal-price' + (tieneOferta ? ' oferta' : '');

    const oldEl = document.getElementById('modal-price-old');
    if (tieneOferta) {
        oldEl.textContent = '$' + parseFloat(p.precio).toLocaleString('es-CO');
        oldEl.style.display = 'inline';
    } else {
        oldEl.style.display = 'none';
    }

    document.getElementById('modal-stock').textContent = `${p.stock} en stock`;

    document.getElementById('modal-btn-cart').onclick = () => {
        addToCart(p.id, p.nombre, precioFinal, p.imagen, p.tienda_nombre);
        closeModalEl();
    };

    const waBtn = document.getElementById('modal-btn-wa');
    if (p.tienda_wa) {
        waBtn.href = `https://wa.me/${p.tienda_wa}?text=Hola,%20estoy%20interesado%20en:%20${encodeURIComponent(p.nombre)}`;
        waBtn.style.display = 'flex';
    } else {
        waBtn.style.display = 'none';
    }

    overlay.classList.add('open');
    document.body.style.overflow = 'hidden';
}

function closeModal(e) {
    if (e && e.target !== document.getElementById('modal-overlay')) return;
    closeModalEl();
}

function closeModalEl() {
    document.getElementById('modal-overlay').classList.remove('open');
    document.body.style.overflow = '';
}

/* ──────────────────────────────────────────────
   TOAST
────────────────────────────────────────────── */
function toast(msg, tipo = '') {
    const div = document.createElement('div');
    div.className = 'toast ' + tipo;
    div.innerHTML = msg;
    document.getElementById('toasts').appendChild(div);
    setTimeout(() => div.remove(), 3200);
}

/* ──────────────────────────────────────────────
   INIT
────────────────────────────────────────────── */
cargarCarrito();
</script>
</body>
</html>