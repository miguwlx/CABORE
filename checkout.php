<?php
session_start();
require_once 'conexion.php';

if (!isset($_SESSION['usuario_id']) || $_SESSION['rol'] !== 'cliente') {
    header('Location: login.html');
    exit;
}

$usuario_id = $_SESSION['usuario_id'];

// Obtener datos del usuario
$stmt = $pdo->prepare("SELECT * FROM usuarios WHERE id = ?");
$stmt->execute([$usuario_id]);
$usuario = $stmt->fetch();

// Procesar pedido enviado
if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['confirmar'])) {
    $carrito_json = $_POST['carrito_json'] ?? '[]';
    $carrito      = json_decode($carrito_json, true);
    $direccion    = trim($_POST['direccion'] ?? '');
    $notas        = trim($_POST['notas'] ?? '');

    if (empty($carrito)) {
        $_SESSION['flash'] = ['tipo' => 'err', 'msg' => 'El carrito está vacío.'];
        header('Location: cliente.php');
        exit;
    }

    if (empty($direccion)) {
        $error = 'Por favor ingresa tu dirección de entrega.';
    } else {
        try {
            $pdo->beginTransaction();

            // Calcular total
            $total = array_reduce($carrito, fn($s, $i) => $s + ($i['precio'] * $i['cantidad']), 0);

            // Crear pedido
            $stmt = $pdo->prepare("
                INSERT INTO pedidos (usuario_id, total, direccion_entrega, notas, estado, created_at)
                VALUES (?, ?, ?, ?, 'pendiente', NOW())
            ");
            $stmt->execute([$usuario_id, $total, $direccion, $notas]);
            $pedido_id = $pdo->lastInsertId();

            // Insertar ítems y descontar stock
            $stmt_item  = $pdo->prepare("
                INSERT INTO pedido_items (pedido_id, producto_id, cantidad, precio_unitario)
                VALUES (?, ?, ?, ?)
            ");
            $stmt_stock = $pdo->prepare("
                UPDATE productos SET stock = stock - ? WHERE id = ? AND stock >= ?
            ");

            foreach ($carrito as $item) {
                $prod_id  = intval($item['id']);
                $cantidad = intval($item['cantidad']);
                $precio   = floatval($item['precio']);

                // Verificar stock real
                $chk = $pdo->prepare("SELECT stock, nombre FROM productos WHERE id = ?");
                $chk->execute([$prod_id]);
                $prod = $chk->fetch();

                if (!$prod || $prod['stock'] < $cantidad) {
                    throw new Exception("Stock insuficiente para: " . ($prod['nombre'] ?? 'producto #'.$prod_id));
                }

                $stmt_item->execute([$pedido_id, $prod_id, $cantidad, $precio]);
                $stmt_stock->execute([$cantidad, $prod_id, $cantidad]);
            }

            $pdo->commit();

            $_SESSION['flash'] = ['tipo' => 'ok', 'msg' => "¡Pedido #$pedido_id realizado con éxito! Te contactaremos pronto."];
            $_SESSION['ultimo_pedido'] = $pedido_id;
            header('Location: mis-pedidos.php');
            exit;

        } catch (Exception $e) {
            $pdo->rollBack();
            $error = $e->getMessage();
        }
    }
}

// Recibir carrito desde cliente.php (POST inicial)
$carrito_json = $_POST['carrito'] ?? '[]';
$carrito = json_decode($carrito_json, true) ?: [];

if (empty($carrito) && !isset($error)) {
    header('Location: cliente.php');
    exit;
}

$total = array_reduce($carrito, fn($s, $i) => $s + ($i['precio'] * $i['cantidad']), 0);
?>
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Caboré — Confirmar pedido</title>
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

/* Navbar */
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
.nav-back {
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: 7px;
    font-size: .875rem;
    color: var(--muted);
    transition: color .15s;
}
.nav-back:hover { color: var(--accent); }

/* Layout */
.page {
    max-width: 920px;
    margin: 40px auto;
    padding: 0 20px 60px;
    display: grid;
    grid-template-columns: 1fr 360px;
    gap: 28px;
    align-items: start;
}

@media (max-width: 760px) {
    .page { grid-template-columns: 1fr; }
}

/* Cards */
.card {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 16px;
    padding: 28px;
}

.card-title {
    font-family: var(--font-head);
    font-size: 1.15rem;
    font-weight: 600;
    margin-bottom: 22px;
    display: flex;
    align-items: center;
    gap: 10px;
}
.card-title i { color: var(--accent); }

/* Form */
.form-group { margin-bottom: 18px; }
.form-label { display: block; font-size: .8rem; font-weight: 600; color: var(--muted); text-transform: uppercase; letter-spacing: .07em; margin-bottom: 7px; }
.form-input, .form-textarea {
    width: 100%;
    padding: 11px 14px;
    border: 1.5px solid var(--border);
    border-radius: 9px;
    font-size: .9rem;
    font-family: var(--font-body);
    color: var(--text);
    background: var(--bg);
    transition: border-color .2s;
}
.form-input:focus, .form-textarea:focus { outline: none; border-color: var(--accent); background: #fff; }
.form-textarea { resize: vertical; min-height: 90px; }

.error-box {
    background: #FDECEA;
    color: #C0392B;
    border-radius: 9px;
    padding: 12px 16px;
    font-size: .875rem;
    font-weight: 500;
    margin-bottom: 18px;
    display: flex;
    align-items: center;
    gap: 9px;
}

/* Resumen */
.order-items { list-style: none; margin-bottom: 20px; }
.order-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px 0;
    border-bottom: 1px solid var(--border);
}
.order-item:last-child { border-bottom: none; }
.order-item-img {
    width: 52px;
    height: 52px;
    border-radius: 8px;
    object-fit: cover;
    background: var(--bg);
    flex-shrink: 0;
}
.order-item-no-img {
    width: 52px;
    height: 52px;
    border-radius: 8px;
    background: var(--bg);
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--border);
    font-size: 1.3rem;
    flex-shrink: 0;
}
.order-item-info { flex: 1; }
.order-item-name { font-size: .875rem; font-weight: 600; margin-bottom: 2px; }
.order-item-store { font-size: .75rem; color: var(--muted); }
.order-item-qty { font-size: .75rem; color: var(--muted); margin-top: 2px; }
.order-item-price { font-size: .9rem; font-weight: 700; }

.divider { border: none; border-top: 1px solid var(--border); margin: 16px 0; }

.total-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: .875rem;
    color: var(--muted);
    margin-bottom: 8px;
}
.total-row.main-total {
    font-size: 1.2rem;
    font-weight: 700;
    color: var(--text);
    margin-top: 12px;
    margin-bottom: 0;
}

.btn-confirm {
    width: 100%;
    margin-top: 20px;
    padding: 14px;
    background: var(--accent);
    color: #fff;
    border: none;
    border-radius: var(--radius);
    font-size: 1rem;
    font-weight: 700;
    font-family: var(--font-body);
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 9px;
    transition: background .2s;
}
.btn-confirm:hover { background: #A8431C; }

.secure-note {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 7px;
    font-size: .75rem;
    color: var(--muted);
    margin-top: 12px;
}
.secure-note i { color: var(--green); }

/* User info strip */
.user-strip {
    display: flex;
    align-items: center;
    gap: 12px;
    background: var(--accent-lt);
    border-radius: 9px;
    padding: 12px 14px;
    margin-bottom: 22px;
}
.user-avatar {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    background: var(--accent);
    color: #fff;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: .85rem;
    flex-shrink: 0;
}
.user-strip-info { font-size: .85rem; }
.user-strip-info strong { display: block; font-weight: 600; }
.user-strip-info span { color: var(--muted); font-size: .78rem; }

.steps {
    display: flex;
    gap: 0;
    margin-bottom: 32px;
    border-radius: 9px;
    overflow: hidden;
    border: 1px solid var(--border);
}
.step {
    flex: 1;
    padding: 10px 14px;
    font-size: .78rem;
    font-weight: 600;
    color: var(--muted);
    background: var(--bg);
    display: flex;
    align-items: center;
    gap: 7px;
    border-right: 1px solid var(--border);
}
.step:last-child { border-right: none; }
.step.active { background: var(--accent-lt); color: var(--accent); }
.step.done   { background: var(--green-lt); color: var(--green); }
.step i { font-size: .85rem; }
</style>
</head>
<body>

<nav class="navbar">
    <a href="cliente.php" class="nav-logo">Caboré</a>
    <a href="cliente.php" class="nav-back">
        <i class="fas fa-arrow-left"></i> Seguir comprando
    </a>
</nav>

<div class="page">

    <!-- Columna izquierda: formulario -->
    <div>
        <!-- Pasos -->
        <div class="steps">
            <div class="step done"><i class="fas fa-shopping-bag"></i> Carrito</div>
            <div class="step active"><i class="fas fa-map-marker-alt"></i> Entrega</div>
            <div class="step"><i class="fas fa-check-circle"></i> Confirmado</div>
        </div>

        <div class="card">
            <div class="card-title"><i class="fas fa-map-marker-alt"></i> Datos de entrega</div>

            <!-- Info del comprador -->
            <div class="user-strip">
                <div class="user-avatar">
                    <?= strtoupper(substr($usuario['nombre'] ?? 'U', 0, 1)) ?>
                </div>
                <div class="user-strip-info">
                    <strong><?= htmlspecialchars($usuario['nombre'] ?? '') ?></strong>
                    <span><?= htmlspecialchars($usuario['correo'] ?? '') ?></span>
                </div>
            </div>

            <?php if (isset($error)): ?>
            <div class="error-box">
                <i class="fas fa-exclamation-circle"></i> <?= htmlspecialchars($error) ?>
            </div>
            <?php endif; ?>

            <form method="POST" action="checkout.php">
                <!-- Carrito oculto -->
                <input type="hidden" name="carrito_json" value="<?= htmlspecialchars($carrito_json) ?>">
                <input type="hidden" name="confirmar" value="1">

                <div class="form-group">
                    <label class="form-label">Dirección completa *</label>
                    <input type="text" name="direccion" class="form-input"
                           placeholder="Ej: Calle 45 # 12-34, Bogotá"
                           value="<?= htmlspecialchars($_POST['direccion'] ?? '') ?>"
                           required>
                </div>

                <div class="form-group">
                    <label class="form-label">Teléfono de contacto</label>
                    <input type="tel" name="telefono" class="form-input"
                    placeholder="Ej: 3001234567"value="<?= htmlspecialchars($_POST['telefono'] ?? '') ?>">
                </div>

                <div class="form-group">
                    <label class="form-label">Notas para el vendedor (opcional)</label>
                    <textarea name="notas" class="form-textarea"
                              placeholder="Color, talla, instrucciones especiales…"><?= htmlspecialchars($_POST['notas'] ?? '') ?></textarea>
                </div>

                <button type="submit" class="btn-confirm">
                    <i class="fas fa-check-circle"></i> Confirmar pedido
                </button>
                <div class="secure-note">
                    <i class="fas fa-lock"></i> Tu información está segura
                </div>
            </form>
        </div>
    </div>

    <!-- Columna derecha: resumen -->
    <div class="card">
        <div class="card-title"><i class="fas fa-receipt"></i> Resumen del pedido</div>

        <ul class="order-items" id="order-items-list">
            <?php foreach ($carrito as $item): ?>
            <li class="order-item">
                <?php if (!empty($item['imagen'])): ?>
                    <img class="order-item-img"
                         src="img/productos/<?= htmlspecialchars($item['imagen']) ?>"
                         alt="<?= htmlspecialchars($item['nombre']) ?>">
                <?php else: ?>
                    <div class="order-item-no-img"><i class="fas fa-box-open"></i></div>
                <?php endif; ?>
                <div class="order-item-info">
                    <div class="order-item-name"><?= htmlspecialchars($item['nombre']) ?></div>
                    <div class="order-item-store"><?= htmlspecialchars($item['tienda']) ?></div>
                    <div class="order-item-qty">Cantidad: <?= intval($item['cantidad']) ?></div>
                </div>
                <div class="order-item-price">
                    $<?= number_format($item['precio'] * $item['cantidad'], 0, ',', '.') ?>
                </div>
            </li>
            <?php endforeach; ?>
        </ul>

        <hr class="divider">

        <div class="total-row">
            <span>Subtotal (<?= count($carrito) ?> producto<?= count($carrito) != 1 ? 's' : '' ?>)</span>
            <span>$<?= number_format($total, 0, ',', '.') ?></span>
        </div>
        <div class="total-row">
            <span>Envío</span>
            <span style="color:var(--green); font-weight:600">A convenir con tienda</span>
        </div>
        <div class="total-row main-total">
            <span>Total</span>
            <span>$<?= number_format($total, 0, ',', '.') ?></span>
        </div>
    </div>

</div>

<script>
// Si el carrito viene vacío desde localStorage (JS), rellenamos el form
const cartRaw = localStorage.getItem('cabore_cart');
if (cartRaw) {
    const cart = JSON.parse(cartRaw);
    // Si el form de carrito_json está vacío (llegamos por JS redirect)
    const hiddenInput = document.querySelector('input[name="carrito_json"]');
    if (hiddenInput && hiddenInput.value === '[]' && cart.length > 0) {
        hiddenInput.value = cartRaw;
    }
}

// Limpiar carrito al confirmar éxito (lo maneja mis-pedidos.php via flash)
<?php if (isset($_SESSION['flash']) && $_SESSION['flash']['tipo'] === 'ok'): ?>
localStorage.removeItem('cabore_cart');
<?php endif; ?>
</script>
</body>
</html>