<?php
include("conexion.php");

$id = $_GET['id'];

$tienda = $conn->query("SELECT * FROM tiendas WHERE id = $id")->fetch_assoc();
$productos = $conn->query("SELECT * FROM productos WHERE tienda_id = $id");

if (!$tienda) {
    echo "Tienda no encontrada";
    exit();
}
?>

<!DOCTYPE html>
<html>
<head>
<style>
body{
    margin:0;
    font-family:Segoe UI;
    background:#f5f7fb;
}

/* HEADER */
.header{
    background:<?php echo $tienda['color'] ?? '#4f46e5'; ?>;
    color:white;
    padding:40px;
    text-align:center;
}

.logo{
    width:80px;
    border-radius:50%;
    margin-bottom:10px;
}

/* PRODUCTOS */
.grid{
    display:grid;
    grid-template-columns:repeat(auto-fill,minmax(200px,1fr));
    gap:20px;
    padding:30px;
}

.producto{
    background:white;
    padding:15px;
    border-radius:15px;
    text-align:center;
    box-shadow:0 10px 25px rgba(0,0,0,0.1);
    transition:0.3s;
}

.producto:hover{
    transform:scale(1.05);
}

.producto img{
    width:100%;
    height:150px;
    object-fit:cover;
    border-radius:10px;
}
</style>
</head>

<body>

<div class="header">

    <?php if($tienda['logo']): ?>
        <img src="<?php echo $tienda['logo']; ?>" class="logo">
    <?php endif; ?>

    <h1><?php echo $tienda['nombre']; ?></h1>
    <p><?php echo $tienda['descripcion']; ?></p>

</div>

<div class="grid">

<?php while($p = $productos->fetch_assoc()): ?>
    <div class="producto">
        <img src="<?php echo $p['imagen']; ?>">
        <h3><?php echo $p['nombre']; ?></h3>
        <p>$<?php echo $p['precio']; ?></p>
    </div>
<?php endwhile; ?>

</div>

</body>
</html>