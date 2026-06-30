<?php
session_start();
include("conexion.php");

// Protección
if (!isset($_SESSION['usuario_id']) || $_SESSION['rol'] !== 'emprendedor') {
    header("Location: login.html"); exit();
}

$usuario_id = (int)$_SESSION['usuario_id'];
$seccion    = $_POST['seccion'] ?? 'info';

// ── Función para subir imagen ──────────────────────────────
function subirImagen($campo, $directorio, $prefijo) {
    if (!isset($_FILES[$campo]) || $_FILES[$campo]['error'] !== UPLOAD_ERR_OK) {
        return null; // No se subió nada
    }

    $ext     = strtolower(pathinfo($_FILES[$campo]['name'], PATHINFO_EXTENSION));
    $allowed = ['jpg','jpeg','png','webp','gif'];

    if (!in_array($ext, $allowed)) return null;
    if ($_FILES[$campo]['size'] > 5 * 1024 * 1024) return null; // máx 5MB

    if (!is_dir($directorio)) mkdir($directorio, 0755, true);

    $filename = $prefijo . '_' . uniqid() . '.' . $ext;
    $ruta     = $directorio . $filename;

    if (move_uploaded_file($_FILES[$campo]['tmp_name'], $ruta)) {
        return $ruta;
    }
    return null;
}

// ── Procesar según sección ─────────────────────────────────
switch ($seccion) {

    // ── INFORMACIÓN ──────────────────────────────────────
    case 'info':
        $nombre      = trim($_POST['nombre']      ?? '');
        $descripcion = trim($_POST['descripcion'] ?? '');
        $ciudad      = trim($_POST['ciudad']      ?? '');

        if (empty($nombre)) {
            $_SESSION['flash'] = ['tipo' => 'err', 'msg' => 'El nombre de la tienda no puede estar vacío.'];
            header("Location: emprendedor.php"); exit();
        }

        $stmt = $conn->prepare(
            "UPDATE tiendas SET nombre=?, descripcion=?, ciudad=? WHERE usuario_id=?"
        );
        $stmt->bind_param("sssi", $nombre, $descripcion, $ciudad, $usuario_id);
        $ok = $stmt->execute();

        $_SESSION['flash'] = $ok
            ? ['tipo' => 'ok',  'msg' => 'Información de la tienda actualizada correctamente.']
            : ['tipo' => 'err', 'msg' => 'Error al guardar. Intenta de nuevo.'];
        break;

    // ── APARIENCIA ────────────────────────────────────────
    case 'apariencia':
        $color = $_POST['color'] ?? '#c9a84c';
        if (!preg_match('/^#[0-9a-fA-F]{6}$/', $color)) $color = '#c9a84c';

        // Logo
        $logo = subirImagen('logo', 'img/logos/', 'logo_' . $usuario_id);

        // Banner
        $banner = subirImagen('banner', 'img/banners/', 'banner_' . $usuario_id);

        // Construir query dinámico según lo que se subió
        if ($logo && $banner) {
            $stmt = $conn->prepare("UPDATE tiendas SET color=?, logo=?, banner=? WHERE usuario_id=?");
            $stmt->bind_param("sssi", $color, $logo, $banner, $usuario_id);
        } elseif ($logo) {
            $stmt = $conn->prepare("UPDATE tiendas SET color=?, logo=? WHERE usuario_id=?");
            $stmt->bind_param("ssi", $color, $logo, $usuario_id);
        } elseif ($banner) {
            $stmt = $conn->prepare("UPDATE tiendas SET color=?, banner=? WHERE usuario_id=?");
            $stmt->bind_param("ssi", $color, $banner, $usuario_id);
        } else {
            $stmt = $conn->prepare("UPDATE tiendas SET color=? WHERE usuario_id=?");
            $stmt->bind_param("si", $color, $usuario_id);
        }

        $ok = $stmt->execute();

        $_SESSION['flash'] = $ok
            ? ['tipo' => 'ok',  'msg' => 'Apariencia actualizada correctamente.']
            : ['tipo' => 'err', 'msg' => 'Error al guardar la apariencia.'];
        break;

    // ── CONTACTO & REDES ─────────────────────────────────
    case 'contacto':
        $whatsapp  = preg_replace('/[^0-9]/', '', $_POST['whatsapp']  ?? ''); // solo dígitos
        $instagram = trim($_POST['instagram'] ?? '');

        // Limpiar @ si lo ponen
        $instagram = ltrim($instagram, '@');

        $stmt = $conn->prepare(
            "UPDATE tiendas SET whatsapp=?, instagram=? WHERE usuario_id=?"
        );
        $stmt->bind_param("ssi", $whatsapp, $instagram, $usuario_id);
        $ok = $stmt->execute();

        $_SESSION['flash'] = $ok
            ? ['tipo' => 'ok',  'msg' => 'Datos de contacto actualizados.']
            : ['tipo' => 'err', 'msg' => 'Error al guardar el contacto.'];
        break;

    default:
        $_SESSION['flash'] = ['tipo' => 'err', 'msg' => 'Sección no válida.'];
}

header("Location: emprendedor.php");
exit();