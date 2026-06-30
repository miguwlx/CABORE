<?php
session_start();
require_once 'conexion.php';

// Verificar sesión y rol
if (!isset($_SESSION['usuario_id']) || $_SESSION['rol'] !== 'cliente') {
    header('Location: login.html');
    exit;
}

$usuario_id = $_SESSION['usuario_id'];

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

// Contar ítems en carrito (session)
$carrito_count = 0;
if (isset($_SESSION['carrito'])) {
    foreach ($_SESSION['carrito'] as $item) {
        $carrito_count += $item['cantidad'];
    }
}

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
<link rel="stylesheet" href="css/cliente.css">
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
                <a href="#"><i class="far fa-user"></i> <?= htmlspecialchars($usuario['nombre']) ?></a>
                <a href="mis-pedidos.php"><i class="fas fa-box"></i> Mis pedidos</a>
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
   CARRITO (localStorage)
────────────────────────────────────────────── */
let cart = JSON.parse(localStorage.getItem('cabore_cart') || '[]');

function saveCart() {
    localStorage.setItem('cabore_cart', JSON.stringify(cart));
    updateCartUI();
}

function addToCart(id, nombre, precio, imagen, tienda) {
    const existing = cart.find(i => i.id === id);
    if (existing) {
        existing.cantidad++;
    } else {
        cart.push({ id, nombre, precio, imagen, tienda, cantidad: 1 });
    }
    saveCart();
    toast(`<i class="fas fa-check-circle"></i> "${nombre.substring(0,28)}" agregado`, 'ok');
}

function removeFromCart(id) {
    cart = cart.filter(i => i.id !== id);
    saveCart();
}

function changeQty(id, delta) {
    const item = cart.find(i => i.id === id);
    if (!item) return;
    item.cantidad += delta;
    if (item.cantidad <= 0) removeFromCart(id);
    else saveCart();
}

function updateCartUI() {
    const total = cart.reduce((s, i) => s + i.precio * i.cantidad, 0);
    const count = cart.reduce((s, i) => s + i.cantidad, 0);

    const badge = document.getElementById('cart-badge');
    badge.textContent = count;
    badge.style.display = count > 0 ? 'flex' : 'none';

    document.getElementById('cart-total').textContent =
        '$' + total.toLocaleString('es-CO');

    const body = document.getElementById('cart-body');
    if (cart.length === 0) {
        body.innerHTML = '<div class="cart-empty"><i class="fas fa-shopping-bag"></i><p>Tu carrito está vacío</p></div>';
        return;
    }

    body.innerHTML = cart.map(item => `
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
}

function closeCart() {
    document.getElementById('cart-overlay').classList.remove('open');
    document.getElementById('cart-drawer').classList.remove('open');
}

function checkout() {
    if (cart.length === 0) { toast('Tu carrito está vacío'); return; }
    // Enviar carrito como form POST a checkout.php
    const form = document.createElement('form');
    form.method = 'POST';
    form.action = 'checkout.php';
    const inp = document.createElement('input');
    inp.type = 'hidden';
    inp.name = 'carrito';
    inp.value = JSON.stringify(cart);
    form.appendChild(inp);
    document.body.appendChild(form);
    form.submit();
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
updateCartUI();
</script>
</body>
</html>