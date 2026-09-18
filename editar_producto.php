<?php
session_start();
include("conexion.php");

if (!isset($_SESSION['usuario_id']) || $_SESSION['rol'] !== 'emprendedor') {
    header("Location: login.html"); exit();
}

$id = isset($_GET['id']) ? (int)$_GET['id'] : 0;
$stmt = $conn->prepare("SELECT * FROM productos WHERE id = ?");
$stmt->bind_param("i", $id);
$stmt->execute();
$producto = $stmt->get_result()->fetch_assoc();

if (!$producto) {
    header("Location: emprendedor.php"); exit();
}
?>
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Editar producto — Caboré</title>
<link href="https://fonts.googleapis.com/css2?family=Playfair+Display:wght@700;900&family=DM+Sans:wght@300;400;500;600&display=swap" rel="stylesheet">
<link rel="stylesheet" href="css/editar_producto.css">
</head>
<body>
<div class="bg-grid"></div>
<div class="box">
    <a class="logo" href="emprendedor.php">Cabo<span>ré</span></a>
    <h2>Editar producto ✏️</h2>
    <p class="subtitle">Actualiza la información del producto</p>

    <form action="actualizar_producto.php" method="POST">
        <input type="hidden" name="id" value="<?= $producto['id'] ?>">

        <div class="form-group">
            <label>Nombre del producto</label>
            <input type="text" name="nombre" value="<?= htmlspecialchars($producto['nombre']) ?>" required>
        </div>
        <div class="form-group">
            <label>Precio (COP)</label>
            <input type="number" name="precio" value="<?= $producto['precio'] ?>" min="0" required>
        </div>
        <div class="form-group">
            <label>Descripción</label>
            <textarea name="descripcion"><?= htmlspecialchars($producto['descripcion'] ?? '') ?></textarea>
        </div>

        <div class="btn-row">
            <button type="submit" class="btn btn-gold">💾 Actualizar</button>
            <a href="emprendedor.php" class="btn btn-ghost">← Cancelar</a>
        </div>
    </form>
</div>
</body>
</html>