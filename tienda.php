<?php
session_start();
include("conexion.php");

$id = isset($_GET['id']) ? (int)$_GET['id'] : 0;

$stmtT = $conn->prepare("SELECT * FROM tiendas WHERE id = ?");
$stmtT->bind_param("i", $id);
$stmtT->execute();
$tienda = $stmtT->get_result()->fetch_assoc();

if (!$tienda) {
    http_response_code(404);
    echo "Tienda no encontrada"; exit();
}

$productos = $conn->query("SELECT * FROM productos WHERE tienda_id = $id ORDER BY id DESC");
$color = htmlspecialchars($tienda['color'] ?? '#c9a84c');

$plantillasPermitidas = ['moderna', 'minimalista', 'elegante', 'colorida'];
$plantilla = $tienda['plantilla'] ?? 'moderna';
if (!in_array($plantilla, $plantillasPermitidas, true)) $plantilla = 'moderna';

// Variables visuales por plantilla
switch ($plantilla) {
    case 'minimalista':
        $v = [
            'bg' => '#fafafa', 'bg2' => '#f0f0f0', 'surface' => '#ffffff',
            'text' => '#111111', 'muted' => 'rgba(17,17,17,0.5)', 'border' => 'rgba(0,0,0,0.1)',
            'radius' => '4px', 'radiusLg' => '4px',
            'fontHeading' => "'DM Sans', sans-serif", 'headerGrad' => '0%',
            'hoverLift' => 'none', 'hoverShadow' => 'none',
        ];
        break;

    case 'elegante':
        $v = [
            'bg' => '#f7f2e9', 'bg2' => '#efe6d3', 'surface' => '#ffffff',
            'text' => '#2a2118', 'muted' => 'rgba(42,33,24,0.6)', 'border' => 'rgba(42,33,24,0.15)',
            'radius' => '10px', 'radiusLg' => '18px',
            'fontHeading' => "'Playfair Display', serif", 'headerGrad' => '12%',
            'hoverLift' => 'translateY(-2px)', 'hoverShadow' => '0 12px 30px rgba(42,33,24,0.12)',
        ];
        break;

    case 'colorida':
        $v = [
            'bg' => '#150a1f', 'bg2' => '#1e0f2b', 'surface' => '#231433',
            'text' => '#fdf7ff', 'muted' => 'rgba(253,247,255,0.55)', 'border' => 'rgba(255,255,255,0.14)',
            'radius' => '26px', 'radiusLg' => '30px',
            'fontHeading' => "'Playfair Display', serif", 'headerGrad' => '28%',
            'hoverLift' => 'translateY(-6px) rotate(-1deg)', 'hoverShadow' => '0 20px 45px rgba(255,60,180,0.25)',
        ];
        break;

    default: // moderna
        $v = [
            'bg' => '#0d0d14', 'bg2' => '#12121f', 'surface' => '#161625',
            'text' => '#f5f0e8', 'muted' => 'rgba(245,240,232,0.45)', 'border' => 'rgba(255,255,255,0.1)',
            'radius' => '16px', 'radiusLg' => '22px',
            'fontHeading' => "'Playfair Display', serif", 'headerGrad' => '20%',
            'hoverLift' => 'translateY(-4px)', 'hoverShadow' => '0 16px 40px rgba(0,0,0,0.35)',
        ];
}
?>
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title><?= htmlspecialchars($tienda['nombre']) ?> — Caboré</title>
<link href="https://fonts.googleapis.com/css2?family=Playfair+Display:wght@700;900&family=DM+Sans:wght@300;400;500;600&display=swap" rel="stylesheet">
<style>
:root {
    --brand: <?= $color ?>;
    --bg:      <?= $v['bg'] ?>;
    --bg2:     <?= $v['bg2'] ?>;
    --surface: <?= $v['surface'] ?>;
    --text:    <?= $v['text'] ?>;
    --muted:   <?= $v['muted'] ?>;
    --border:  <?= $v['border'] ?>;
    --radius:    <?= $v['radius'] ?>;
    --radius-lg: <?= $v['radiusLg'] ?>;
}
*,*::before,*::after{margin:0;padding:0;box-sizing:border-box;}
body{font-family:'DM Sans',sans-serif;background:var(--bg);color:var(--text);min-height:100vh;}

/* HEADER */
.header{
    background:linear-gradient(135deg, color-mix(in srgb, var(--brand) <?= $v['headerGrad'] ?>, var(--bg)), var(--bg));
    border-bottom:1px solid var(--border);
    padding:60px 40px 50px;
    text-align:center;
    position:relative;
    overflow:hidden;
}
.header::before{
    content:'';
    position:absolute; inset:0;
    background:radial-gradient(ellipse at 50% 0%, color-mix(in srgb,var(--brand) 25%,transparent), transparent 70%);
    pointer-events:none;
}
<?php if ($plantilla === 'colorida'): ?>
.header::after{
    content:'';
    position:absolute; inset:0;
    background:
        radial-gradient(circle at 15% 20%, rgba(255,80,190,0.25), transparent 40%),
        radial-gradient(circle at 85% 10%, rgba(60,210,255,0.22), transparent 45%);
    pointer-events:none;
}
<?php endif; ?>
.header-nav{
    position:absolute; top:20px; left:20px;
    font-family:'Playfair Display',serif;
    font-size:18px; font-weight:900;
    color:var(--muted);
    text-decoration:none;
}
.header-nav em{color:var(--brand);font-style:normal;}

.tienda-logo{
    width:90px;height:90px;
    border-radius:var(--radius-lg);
    object-fit:cover;
    margin:0 auto 20px;
    display:block;
    border:3px solid var(--border);
    background:var(--bg2);
}
.tienda-logo-ph{
    width:90px;height:90px;
    border-radius:var(--radius-lg);
    background:linear-gradient(135deg,var(--brand),color-mix(in srgb,var(--brand) 60%,#000));
    margin:0 auto 20px;
    display:flex;align-items:center;justify-content:center;
    font-size:36px;
    border:3px solid var(--border);
}
.header h1{
    font-family:<?= $v['fontHeading'] ?>;
    font-size:clamp(28px,4vw,44px);
    font-weight:900;
    margin-bottom:10px;
    <?= $plantilla === 'elegante' ? 'letter-spacing:0.5px;' : '' ?>
}
.header p{color:var(--muted);font-size:16px;font-weight:300;max-width:500px;margin:0 auto;}

.color-tag {
    display:inline-flex;align-items:center;gap:8px;
    margin-top:18px;
    padding:5px 14px;
    border:1px solid var(--border);
    border-radius:20px;
    font-size:12px;
    color:var(--muted);
    <?= $plantilla === 'elegante' ? 'text-transform:uppercase;letter-spacing:1.5px;' : '' ?>
}

.color-dot{width:10px;height:10px;border-radius:50%;background:var(--brand);}

/* MAIN */
.contenido{max-width:1200px;margin:0 auto;padding:40px 30px;}

.section-title{
    font-family:<?= $v['fontHeading'] ?>;
    font-size:24px;font-weight:700;
    margin-bottom:24px;
    padding-bottom:14px;
    border-bottom:1px solid var(--border);
}

.grid{
    display:grid;
    grid-template-columns:repeat(auto-fill,minmax(220px,1fr));
    gap:18px;
}

.producto{
    background:var(--surface);
    border:1px solid var(--border);
    border-radius:var(--radius);
    overflow:hidden;
    transition:all 0.3s;
}
.producto:hover{
    border-color:var(--brand);
    transform:<?= $v['hoverLift'] ?>;
    box-shadow:<?= $v['hoverShadow'] ?>;
}
.producto img{width:100%;height:170px;object-fit:cover;display:block;background:var(--bg2);}
.producto-ph{width:100%;height:170px;background:linear-gradient(135deg,var(--bg2),var(--surface));display:flex;align-items:center;justify-content:center;font-size:44px;}
.producto-body{padding:16px;}
.producto-nombre{font-weight:600;font-size:15px;margin-bottom:6px;line-height:1.3;}
.producto-precio{
    font-family:<?= $v['fontHeading'] ?>;
    font-size:20px;font-weight:700;
    color:var(--brand);
}
.producto-desc{color:var(--muted);font-size:13px;margin-top:6px;line-height:1.5;}

.empty{text-align:center;padding:80px;color:var(--muted);}
.empty span{font-size:56px;display:block;margin-bottom:16px;}

footer{
    text-align:center;
    padding:30px;
    color:var(--muted);
    font-size:13px;
    border-top:1px solid var(--border);
    margin-top:60px;
}
footer a{color:var(--brand);text-decoration:none;}
</style>
</head>
<body>

<div class="header">
    <a class="header-nav" href="landing.html">Cabo<em>ré</em></a>

    <?php if(!empty($tienda['logo'])): ?>
        <img src="<?= htmlspecialchars($tienda['logo']) ?>" class="tienda-logo">
    <?php else: ?>
        <div class="tienda-logo-ph">🏪</div>
    <?php endif; ?>

    <h1><?= htmlspecialchars($tienda['nombre']) ?></h1>
    <p><?= htmlspecialchars($tienda['descripcion'] ?? '') ?></p>
        <div class="color-tag">
        <div class="color-dot"></div>
        Tienda en Caboré Marketplace
    </div>

    <?php if (($_SESSION['rol'] ?? '') === 'cliente'): ?>
        <div style="margin-top:14px">
            <a href="iniciar_chat.php?tienda_id=<?= $tienda['id'] ?>"
               style="display:inline-flex;align-items:center;gap:6px;padding:8px 16px;border-radius:20px;border:1px solid var(--border);color:var(--brand);text-decoration:none;font-size:13px">
                💬 Contactar tienda
            </a>
        </div>
    <?php elseif (!isset($_SESSION['usuario_id'])): ?>
        <div style="margin-top:14px">
            <a href="login.html"
               style="display:inline-flex;align-items:center;gap:6px;padding:8px 16px;border-radius:20px;border:1px solid var(--border);color:var(--muted);text-decoration:none;font-size:13px">
                💬 Inicia sesión para contactar
            </a>
        </div>
    <?php endif; ?>
</div>

<div class="contenido">
    <div class="section-title">Productos disponibles</div>

    <?php
    $lista = [];
    while($p = $productos->fetch_assoc()) $lista[] = $p;
    ?>

    <?php if(empty($lista)): ?>
        <div class="empty">
            <span>📦</span>
            <p>Esta tienda aún no tiene productos publicados.</p>
        </div>
    <?php else: ?>
        <div class="grid">
        <?php foreach($lista as $p): ?>
            <div class="producto">
                <?php if(!empty($p['imagen'])): ?>
                    <img src="<?= htmlspecialchars($p['imagen']) ?>" alt="<?= htmlspecialchars($p['nombre']) ?>">
                <?php else: ?>
                    <div class="producto-ph">🛍️</div>
                <?php endif; ?>
                <div class="producto-body">
                    <div class="producto-nombre"><?= htmlspecialchars($p['nombre']) ?></div>
                    <div class="producto-precio">$<?= number_format($p['precio'],0,',','.') ?></div>
                    <?php if(!empty($p['descripcion'])): ?>
                        <div class="producto-desc"><?= htmlspecialchars($p['descripcion']) ?></div>
                    <?php endif; ?>
                </div>
            </div>
        <?php endforeach; ?>
        </div>
    <?php endif; ?>
</div>

<footer>
    Tienda creada en <a href="landing.html">Caboré Marketplace</a>
</footer>
</body>
</html>