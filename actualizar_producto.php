<?php
include("conexion.php");

$id = $_GET['id'];

$sql = "SELECT * FROM productos WHERE id=$id";
$resultado = $conn->query($sql);
$producto = $resultado->fetch_assoc();
?>

<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<title>Editar producto</title>
</head>
<body>

<h2>Editar producto</h2>

<form action="actualizar_producto.php" method="POST">
    <input type="hidden" name="id" value="<?php echo $producto['id']; ?>">

    <input type="text" name="nombre" value="<?php echo $producto['nombre']; ?>">
    <input type="number" name="precio" value="<?php echo $producto['precio']; ?>">
    <textarea name="descripcion"><?php echo $producto['descripcion']; ?></textarea>

    <button>Actualizar</button>
</form>

</body>
</html>