<?php
session_start();
include("conexion.php");

$token = $_GET['token'] ?? $_POST['token'] ?? '';
$error = '';
$exito = false;

if ($token === '') {
    header("Location: olvide_password.html");
    exit();
}

$stmt = $conn->prepare("SELECT id, reset_expira FROM usuarios WHERE reset_token = ?");
$stmt->bind_param("s", $token);
$stmt->execute();
$usuario = $stmt->get_result()->fetch_assoc();

$token_valido = $usuario && strtotime($usuario['reset_expira']) > time();

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $nueva     = $_POST['nueva_contrasena'] ?? '';
    $confirmar = $_POST['confirmar_contrasena'] ?? '';

    if (!$token_valido) {
        $error = "El enlace expiró o no es válido. Solicita uno nuevo.";
    } elseif (strlen($nueva) < 8) {
        $error = "La contraseña debe tener al menos 8 caracteres.";
    } elseif ($nueva !== $confirmar) {
        $error = "Las contraseñas no coinciden.";
    } else {
        $hash = password_hash($nueva, PASSWORD_DEFAULT);
        $upd  = $conn->prepare("UPDATE usuarios SET contrasena = ?, reset_token = NULL, reset_expira = NULL WHERE id = ?");
        $upd->bind_param("si", $hash, $usuario['id']);
        $upd->execute();
        $exito = true;
    }
}
?>
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Caboré — Restablecer contraseña</title>
<link href="https://fonts.googleapis.com/css2?family=Cormorant+Garamond:ital,wght@0,400;0,600;0,700;1,400&family=Outfit:wght@300;400;500;600;700&display=swap" rel="stylesheet">
<link rel="stylesheet" href="css/recuperacion.css">
</head>
<body>

<div class="noise"></div>
<div class="bg-glow a"></div>
<div class="bg-glow b"></div>

<div class="recover-card">

  <?php if ($exito): ?>
    <div class="recover-icon" style="background:linear-gradient(135deg,#34d399,#0f9d63)">✅</div>
    <div class="recover-eyebrow">Listo</div>
    <div class="recover-title">Contraseña <em>actualizada</em></div>
    <p class="recover-sub">Tu contraseña se cambió correctamente. Ya puedes iniciar sesión con tu nueva contraseña.</p>
    <a href="login.html" class="btn-gold" style="text-decoration:none">Iniciar sesión →</a>

  <?php elseif (!$token_valido): ?>
    <div class="recover-icon" style="background:linear-gradient(135deg,#f87171,#dc2626)">⛔</div>
    <div class="recover-eyebrow">Enlace no válido</div>
    <div class="recover-title">Este enlace <em>expiró</em></div>
    <p class="recover-sub">Los enlaces de recuperación duran 1 hora por seguridad. Solicita uno nuevo para continuar.</p>
    <a href="olvide_password.html" class="btn-gold" style="text-decoration:none">Solicitar nuevo enlace</a>

  <?php else: ?>
    <a href="login.html" class="recover-back">← Volver a iniciar sesión</a>
    <div class="recover-icon">🔒</div>
    <div class="recover-eyebrow">Último paso</div>
    <div class="recover-title">Crea tu <em>nueva contraseña</em></div>
    <p class="recover-sub">Elige una contraseña segura de al menos 8 caracteres.</p>

    <?php if ($error): ?>
      <div class="error-box">⚠️ <?= htmlspecialchars($error) ?></div>
    <?php endif; ?>

    <form method="POST" id="resetForm">
      <input type="hidden" name="token" value="<?= htmlspecialchars($token) ?>">
      <div class="field">
        <label class="field-label">Nueva contraseña</label>
        <div class="field-wrap">
          <input type="password" name="nueva_contrasena" id="nueva_contrasena" required minlength="8" autocomplete="new-password" placeholder="Mínimo 8 caracteres">
        </div>
      </div>
      <div class="field">
        <label class="field-label">Confirmar contraseña</label>
        <div class="field-wrap">
          <input type="password" name="confirmar_contrasena" id="confirmar_contrasena" required minlength="8" autocomplete="new-password" placeholder="Repite tu nueva contraseña">
        </div>
      </div>
      <button type="submit" class="btn-gold">Guardar nueva contraseña</button>
    </form>
  <?php endif; ?>
</div>

<script src="js/validaciones.js"></script>
</body>
</html>