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
<link rel="stylesheet" href="css/login.css">
</head>
<body>
<div class="page" style="display:flex;align-items:center;justify-content:center;min-height:100vh">
  <div class="panel-area" style="max-width:420px">
    <div class="panel-title">Restablecer contraseña</div>

    <?php if ($exito): ?>
      <p class="panel-sub">Tu contraseña se actualizó correctamente.</p>
      <a href="login.html" class="btn-submit btn--blue" style="display:block;text-align:center;text-decoration:none;margin-top:16px">Iniciar sesión →</a>

    <?php elseif (!$token_valido): ?>
      <p class="panel-sub">Este enlace ya no es válido o expiró.</p>
      <a href="olvide_password.html" class="btn-submit btn--blue" style="display:block;text-align:center;text-decoration:none;margin-top:16px">Solicitar un nuevo enlace</a>

    <?php else: ?>
      <?php if ($error): ?>
        <div class="error-box visible">⚠️ <?= htmlspecialchars($error) ?></div>
      <?php endif; ?>

      <form method="POST" id="resetForm">
        <input type="hidden" name="token" value="<?= htmlspecialchars($token) ?>">
        <div class="field">
          <label class="field-label">Nueva contraseña</label>
          <div class="field-wrap focus-blue">
            <input type="password" name="nueva_contrasena" id="nueva_contrasena" required minlength="8" autocomplete="new-password">
          </div>
        </div>
        <div class="field">
          <label class="field-label">Confirmar contraseña</label>
          <div class="field-wrap focus-blue">
            <input type="password" name="confirmar_contrasena" id="confirmar_contrasena" required minlength="8" autocomplete="new-password">
          </div>
        </div>
        <button type="submit" class="btn-submit btn--blue">Guardar nueva contraseña</button>
      </form>
    <?php endif; ?>
  </div>
</div>

<script src="js/validaciones.js"></script>
</body>
</html>