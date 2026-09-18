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

// Cantidad de pedidos realizados (dato informativo)
$stmt = $pdo->prepare("SELECT COUNT(*) FROM pedidos WHERE usuario_id = ?");
$stmt->execute([$usuario_id]);
$total_pedidos = (int) $stmt->fetchColumn();

$flash = $_SESSION['flash'] ?? null;
unset($_SESSION['flash']);
?>
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Caboré — Mi perfil</title>
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
    max-width: 720px;
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

/* Perfil header card */
.profile-hero {
    background: linear-gradient(135deg, var(--accent), #A8431C);
    border-radius: 18px;
    padding: 28px 26px;
    display: flex;
    align-items: center;
    gap: 18px;
    margin-bottom: 24px;
    color: #fff;
    box-shadow: var(--shadow-lg);
}
.profile-avatar {
    width: 64px;
    height: 64px;
    border-radius: 50%;
    background: rgba(255,255,255,.18);
    border: 2px solid rgba(255,255,255,.4);
    display: flex;
    align-items: center;
    justify-content: center;
    font-family: var(--font-head);
    font-size: 1.6rem;
    font-weight: 700;
    flex-shrink: 0;
}
.profile-hero-info h2 { font-family: var(--font-head); font-size: 1.3rem; font-weight: 600; margin-bottom: 3px; }
.profile-hero-info p  { font-size: .85rem; opacity: .9; }
.profile-hero-stat {
    margin-left: auto;
    text-align: center;
    background: rgba(255,255,255,.14);
    border-radius: 12px;
    padding: 10px 18px;
}
.profile-hero-stat strong { display: block; font-size: 1.3rem; font-family: var(--font-head); }
.profile-hero-stat span { font-size: .72rem; opacity: .85; text-transform: uppercase; letter-spacing: .05em; }

.card {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 16px;
    padding: 28px;
}
.card-title {
    font-family: var(--font-head);
    font-size: 1.1rem;
    font-weight: 600;
    margin-bottom: 20px;
    display: flex;
    align-items: center;
    gap: 10px;
}
.card-title i { color: var(--accent); }

.form-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px 18px;
}
.form-group.full { grid-column: 1 / -1; }
.form-group { margin-bottom: 4px; }
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
.form-input:disabled { color: var(--muted); cursor: not-allowed; }
.form-input:focus, .form-textarea:focus { outline: none; border-color: var(--accent); background: #fff; }
.form-hint { font-size: .75rem; color: var(--muted); margin-top: 6px; }

.btn-save {
    margin-top: 24px;
    padding: 13px 26px;
    background: var(--accent);
    color: #fff;
    border: none;
    border-radius: var(--radius);
    font-size: .92rem;
    font-weight: 700;
    font-family: var(--font-body);
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    gap: 9px;
    transition: background .2s;
}
.btn-save:hover { background: #A8431C; }

.address-note {
    display: flex;
    gap: 10px;
    background: var(--accent-lt);
    border-radius: 10px;
    padding: 12px 14px;
    font-size: .8rem;
    color: var(--accent);
    margin-bottom: 20px;
    align-items: flex-start;
}
.address-note i { margin-top: 2px; }

@media (max-width: 560px) {
    .form-grid { grid-template-columns: 1fr; }
    .profile-hero { flex-wrap: wrap; }
    .profile-hero-stat { margin-left: 0; }
}
</style>
</head>
<body>

<nav class="navbar">
    <a href="cliente.php" class="nav-logo">Caboré</a>
    <div class="nav-links">
        <a href="cliente.php"    class="nav-link"><i class="fas fa-store"></i> Tiendas</a>
        <a href="favoritos.php"  class="nav-link"><i class="far fa-heart"></i> Favoritos</a>
        <a href="perfil.php"     class="nav-link active"><i class="fas fa-user"></i> Mi perfil</a>
        <a href="mis_pedidos.php" class="nav-link"><i class="fas fa-box"></i> Mis pedidos</a>
        <a href="logout.php"     class="nav-link"><i class="fas fa-sign-out-alt"></i> Salir</a>
    </div>
</nav>

<div class="page">

    <div class="page-header">
        <h1>Mi perfil</h1>
        <p>Guarda tus datos de contacto y dirección para agilizar tus próximas compras.</p>
    </div>

    <?php if ($flash): ?>
    <div class="flash <?= $flash['tipo'] ?>">
        <i class="fas <?= $flash['tipo']==='ok' ? 'fa-check-circle' : 'fa-exclamation-circle' ?>"></i>
        <?= htmlspecialchars($flash['msg']) ?>
    </div>
    <?php endif; ?>

    <div class="profile-hero">
        <div class="profile-avatar"><?= strtoupper(substr($usuario['nombre'], 0, 1)) ?></div>
        <div class="profile-hero-info">
            <h2><?= htmlspecialchars($usuario['nombre']) ?></h2>
            <p><?= htmlspecialchars($usuario['correo']) ?></p>
        </div>
        <div class="profile-hero-stat">
            <strong><?= $total_pedidos ?></strong>
            <span>Pedido<?= $total_pedidos != 1 ? 's' : '' ?></span>
        </div>
    </div>

    <div class="card">
        <div class="card-title"><i class="fas fa-map-marker-alt"></i> Datos de contacto y dirección</div>

        <div class="address-note">
            <i class="fas fa-circle-info"></i>
            <span>Estos datos se usarán para pre-llenar automáticamente el formulario cuando finalices una compra. Siempre podrás editarlos en el momento del pago.</span>
        </div>

        <form action="actualizar_perfil.php" method="POST">
            <div class="form-grid">
                <div class="form-group">
                    <label class="form-label">Nombre</label>
                    <input type="text" class="form-input" value="<?= htmlspecialchars($usuario['nombre']) ?>" disabled>
                </div>
                <div class="form-group">
                    <label class="form-label">Correo</label>
                    <input type="text" class="form-input" value="<?= htmlspecialchars($usuario['correo']) ?>" disabled>
                </div>

                <div class="form-group">
                    <label class="form-label">Teléfono de contacto</label>
                    <input type="tel" name="telefono" class="form-input"
                           placeholder="Ej: 3001234567"
                           value="<?= htmlspecialchars($usuario['telefono'] ?? '') ?>">
                </div>
                <div class="form-group">
                    <label class="form-label">Ciudad</label>
                    <input type="text" name="ciudad" class="form-input"
                           placeholder="Ej: Bogotá"
                           value="<?= htmlspecialchars($usuario['ciudad'] ?? '') ?>">
                </div>

                <div class="form-group full">
                    <label class="form-label">Dirección de entrega</label>
                    <input type="text" name="direccion" class="form-input"
                           placeholder="Ej: Calle 45 # 12-34, Apto 302, Bogotá"
                           value="<?= htmlspecialchars($usuario['direccion'] ?? '') ?>">
                    <div class="form-hint">Incluye barrio, número de apartamento o referencias que le sirvan al repartidor.</div>
                </div>
            </div>

            <button type="submit" class="btn-save">
                <i class="fas fa-check"></i> Guardar cambios
            </button>
        </form>
    </div>

</div>

<script>
const flash = document.querySelector('.flash');
if (flash) setTimeout(() => flash.style.opacity = '0', 4000);
</script>
</body>
</html>