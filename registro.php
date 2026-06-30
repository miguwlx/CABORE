<?php
session_start();
include("conexion.php");

$nombre    = trim($_POST['nombre']    ?? '');
$correo    = trim($_POST['correo']    ?? '');
$contrasena = $_POST['contrasena']   ?? '';
$rol       = $_POST['rol']           ?? 'cliente';

// Validaciones básicas
if (!$nombre || !$correo || !$contrasena || strlen($contrasena) < 6) {
    $error = "Completa todos los campos correctamente.";
} elseif (!in_array($rol, ['cliente','emprendedor'])) {
    $error = "Rol inválido.";
} else {
    // Verificar correo duplicado
    $check = $conn->prepare("SELECT id FROM usuarios WHERE correo = ?");
    $check->bind_param("s", $correo);
    $check->execute();
    $check->store_result();

    if ($check->num_rows > 0) {
        $error = "Este correo ya está registrado.";
    } else {
        $hash = password_hash($contrasena, PASSWORD_DEFAULT);
        $stmt = $conn->prepare(
            "INSERT INTO usuarios (nombre, correo, contrasena, rol) VALUES (?, ?, ?, ?)"
        );
        $stmt->bind_param("ssss", $nombre, $correo, $hash, $rol);
        $exito = $stmt->execute();

        if (!$exito) {
            $error = "Error interno. Intenta de nuevo.";
        } else {
            // ── Cuenta creada: iniciar sesión automáticamente ──
            $nuevo_id = $stmt->insert_id;

            session_regenerate_id(true);
            $_SESSION['usuario_id'] = $nuevo_id;
            $_SESSION['nombre']     = $nombre;
            $_SESSION['rol']        = $rol;

            // Redirigir directo al panel correspondiente según el rol elegido
            if ($rol === 'emprendedor') {
                header("Location: emprendedor.php");
            } else {
                header("Location: cliente.php");
            }
            exit();
        }
    }
}
?>
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Caboré — Registro</title>
<link href="https://fonts.googleapis.com/css2?family=Playfair+Display:wght@700;900&family=DM+Sans:wght@300;400;500;600&display=swap" rel="stylesheet">
<style>
:root { --gold:#c9a84c; --dark:#0d0d14; --light:#f5f0e8; --border:rgba(201,168,76,0.25); }
*, *::before, *::after { margin:0; padding:0; box-sizing:border-box; }
body {
    font-family:'DM Sans',sans-serif;
    background:var(--dark); color:var(--light);
    min-height:100vh; display:flex; align-items:center; justify-content:center;
}
.bg-grid {
    position:fixed; inset:0;
    background-image: linear-gradient(rgba(201,168,76,0.04) 1px,transparent 1px),
                      linear-gradient(90deg,rgba(201,168,76,0.04) 1px,transparent 1px);
    background-size:60px 60px; pointer-events:none;
}
.card {
    position:relative; z-index:1;
    background:rgba(255,255,255,0.05);
    backdrop-filter:blur(20px);
    border:1px solid var(--border);
    border-radius:24px;
    padding:52px 44px;
    text-align:center;
    width:380px;
    animation: pop 0.6s cubic-bezier(0.22,1,0.36,1) both;
}
.icon { font-size:64px; margin-bottom:20px; display:block; }
h2 { font-family:'Playfair Display',serif; font-size:28px; margin-bottom:10px; }
p  { color:rgba(245,240,232,0.5); font-size:15px; margin-bottom:24px; }
.btn {
    display:inline-block; padding:13px 28px;
    background:linear-gradient(135deg,var(--gold),#a8832a);
    color:#0d0d14; border:none; border-radius:12px;
    font-family:'DM Sans',sans-serif; font-size:15px; font-weight:700;
    cursor:pointer; text-decoration:none; transition:all 0.3s;
}
.btn:hover { transform:translateY(-2px); box-shadow:0 12px 32px rgba(201,168,76,0.35); }
.btn-ghost {
    display:inline-block; padding:12px 28px;
    background:transparent; border:1px solid var(--border);
    color:var(--gold); border-radius:12px;
    font-family:'DM Sans',sans-serif; font-size:15px; font-weight:600;
    cursor:pointer; text-decoration:none; transition:all 0.3s; margin-left:10px;
}
.btn-ghost:hover { background:rgba(201,168,76,0.08); }
.logo {
    position:fixed; top:28px; left:36px;
    font-family:'Playfair Display',serif; font-size:26px; font-weight:900;
    color:var(--gold); z-index:100;
}
.logo span { color:var(--light); }
@keyframes pop {
    from { opacity:0; transform:scale(0.9) translateY(20px); }
    to   { opacity:1; transform:scale(1) translateY(0); }
}
</style>
</head>
<body>
<div class="bg-grid"></div>
<div class="logo">Cabo<span>ré</span></div>

<div class="card">
<?php if (isset($error)): ?>
    <span class="icon">⚠️</span>
    <h2>Ups</h2>
    <p><?= htmlspecialchars($error) ?></p>
    <a href="registro (1).html" class="btn">Intentar de nuevo</a>
    <a href="login.html" class="btn-ghost">Login</a>

<?php else: ?>
    <span class="icon">❌</span>
    <h2>Error inesperado</h2>
    <p>No se pudo completar el registro.</p>
    <a href="registro (1).html" class="btn">Volver</a>
<?php endif; ?>
</div>
</body>
</html>