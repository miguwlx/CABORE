<?php
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
    --dark:  #0d0d14;
    --mid:   #12121f;
    --card:  #161625;
    --light: #f5f0e8;
    --muted: rgba(245,240,232,0.45);
    --border:rgba(255,255,255,0.1);
}
*,*::before,*::after{margin:0;padding:0;box-sizing:border-box;}
body{font-family:'DM Sans',sans-serif;background:var(--dark);color:var(--light);min-height:100vh;}

/* HEADER */
.header{
    background:linear-gradient(135deg, color-mix(in srgb, var(--brand) 20%, #0d0d14), #0d0d14);
    border-bottom:1px solid rgba(255,255,255,0.08);
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
.header-nav{
    position:absolute; top:20px; left:20px;
    font-family:'Playfair Display',serif;
    font-size:18px; font-weight:900;
    color:rgba(245,240,232,0.6);
    text-decoration:none;
}
.header-nav em{color:var(--brand);font-style:normal;}

.tienda-logo{
    width:90px;height:90px;
    border-radius:22px;
    object-fit:cover;
    margin:0 auto 20px;
    display:block;
    border:3px solid rgba(255,255,255,0.15);
    background:var(--mid);
}
.tienda-logo-ph{
    width:90px;height:90px;
    border-radius:22px;
    background:linear-gradient(135deg,var(--brand),color-mix(in srgb,var(--brand) 60%,#000));
    margin:0 auto 20px;
    display:flex;align-items:center;justify-content:center;
    font-size:36px;
    border:3px solid rgba(255,255,255,0.15);
}
.header h1{
    font-family:'Playfair Display',serif;
    font-size:clamp(28px,4vw,44px);
    font-weight:900;
    margin-bottom:10px;
}
.header p{color:var(--muted);font-size:16px;font-weight:300;max-width:500px;margin:0 auto;}

.color-tag{
    display:inline-flex;align-items:center;gap:8px;
    margin-top:18px;
    padding:5px 14px;
    border:1px solid rgba(255,255,255,0.12);
    border-radius:20px;
    font-size:12px;
    color:var(--muted);
}
.color-dot{width:10px;height:10px;border-radius:50%;background:var(--brand);}

/* MAIN */
.contenido{max-width:1200px;margin:0 auto;padding:40px 30px;}

.section-title{
    font-family:'Playfair Display',serif;
    font-size:24px;font-weight:700;
    margin-bottom:24px;
    padding-bottom:14px;
    border-bottom:1px solid rgba(255,255,255,0.07);
}

.grid{
    display:grid;
    grid-template-columns:repeat(auto-fill,minmax(220px,1fr));
    gap:18px;
}

.producto{
    background:var(--card);
    border:1px solid var(--border);
    border-radius:16px;
    overflow:hidden;
    transition:all 0.3s;
}
.producto:hover{border-color:var(--brand);transform:translateY(-4px);box-shadow:0 16px 40px rgba(0,0,0,0.35);}
.producto img{width:100%;height:170px;object-fit:cover;display:block;background:var(--mid);}
.producto-ph{width:100%;height:170px;background:linear-gradient(135deg,#1a1a2e,var(--mid));display:flex;align-items:center;justify-content:center;font-size:44px;}
.producto-body{padding:16px;}
.producto-nombre{font-weight:600;font-size:15px;margin-bottom:6px;line-height:1.3;}
.producto-precio{
    font-family:'Playfair Display',serif;
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
    border-top:1px solid rgba(255,255,255,0.06);
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