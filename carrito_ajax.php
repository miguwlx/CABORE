<?php
include("conexion.php");

$nombre = $_POST['nombre'];
$correo = $_POST['correo'];
$contrasena = $_POST['contrasena'];
$rol = $_POST['rol'] ?? '';

$sql = "INSERT INTO usuarios (nombre, correo, contraseña, rol)
        VALUES ('$nombre', '$correo', '$contrasena', '$rol')";

$exito = false;

if ($conn->query($sql)) {
    $exito = true;
}
?>

<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<title>Registro</title>

<style>
body {
    height: 100vh;
    display: flex;
    justify-content: center;
    align-items: center;
    font-family: 'Segoe UI';
    background: linear-gradient(135deg, 135deg, #e6f0ff, #cce0ff);
}

/* CARD */
.card {
    background: white;
    padding: 40px;
    border-radius: 15px;
    text-align: center;
    width: 320px;
    box-shadow: 0px 10px 30px rgba(0,0,0,0.2);
    animation: aparecer 0.6s ease;
}

/* ICONO */
.icono {
    font-size: 50px;
    margin-bottom: 15px;
}

/* TEXTO */
h2 {
    margin-bottom: 10px;
}

/* BOTONES */
button {
    margin-top: 15px;
    padding: 10px 20px;
    border: none;
    border-radius: 8px;
    background: #2b7cff;
    color: white;
    cursor: pointer;
}

/* ANIMACIÓN */
@keyframes aparecer {
    from { opacity: 0; transform: scale(0.8); }
    to { opacity: 1; transform: scale(1); }
}
</style>

</head>

<body>

<div class="card">

<?php if($exito): ?>

    <div class="icono">✅</div>
    <h2>Registro exitoso</h2>
    <p>Tu cuenta fue creada correctamente</p>

    <button onclick="window.location.href='index.html'">Ir al login</button>

<?php else: ?>

    <div class="icono">❌</div>
    <h2>Error</h2>
    <p>No se pudo registrar el usuario</p>

<?php endif; ?>

</div>

</body>
</html>