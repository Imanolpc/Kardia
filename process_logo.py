import os
import sys
import glob
import subprocess

# Asegurar que Pillow esté instalado
try:
    from PIL import Image, ImageDraw
except ImportError:
    print("Instalando Pillow para procesamiento de imágenes...")
    subprocess.check_call([sys.executable, "-m", "pip", "install", "Pillow"])
    from PIL import Image, ImageDraw

def find_logo():
    # Buscar archivos de logo en la raíz con extensiones comunes
    extensions = ["png", "jpg", "jpeg", "webp"]
    for ext in extensions:
        files = glob.glob(f"logo*.{ext}")
        if files:
            return files[0]
    return None

def make_circle_mask(size):
    mask = Image.new("L", size, 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0) + size, fill=255)
    return mask

def process():
    logo_path = find_logo()
    if not logo_path:
        print("ERROR: No se encontró ningún archivo de logo (ej. 'logo.png', 'logo.jpg') en la raíz del proyecto.")
        print("Por favor, guarda tu imagen en esta carpeta con el nombre 'logo.png' e intenta de nuevo.")
        return False

    print(f"Procesando el logo de entrada: {logo_path}")
    img = Image.open(logo_path)
    
    # 1. Determinar el color de fondo predominante de las esquinas (para extenderlo si no es cuadrado)
    # Analizamos los píxeles de las esquinas
    width, height = img.size
    corners = [img.getpixel((0, 0)), img.getpixel((width - 1, 0)), 
               img.getpixel((0, height - 1)), img.getpixel((width - 1, height - 1))]
    
    # Si la imagen tiene transparencia, usamos fondo transparente, de lo contrario usamos el color de la esquina
    if img.mode == 'RGBA' and any(c[3] == 0 for c in corners if len(c) > 3):
        bg_color = (0, 0, 0, 0)
    else:
        # Usamos el color de la esquina predominante
        bg_color = corners[0]

    print(f"Color de fondo detectado: {bg_color}")

    # 2. Hacer la imagen cuadrada (512x512) para Google Play Store
    play_store_size = (512, 512)
    play_store_img = Image.new(img.mode, play_store_size, bg_color)
    
    # Redimensionar el logo original manteniendo la relación de aspecto para que quepa en el lienzo de 512x512
    max_dimension = 512
    # Ajustamos al 90% para dejar un margen estético
    scale_factor = 0.9
    new_width = int(width * (max_dimension * scale_factor / max(width, height)))
    new_height = int(height * (max_dimension * scale_factor / max(width, height)))
    
    resized_logo = img.resize((new_width, new_height), Image.Resampling.LANCZOS)
    
    # Centrar en el lienzo de 512x512
    x = (512 - new_width) // 2
    y = (512 - new_height) // 2
    play_store_img.paste(resized_logo, (x, y), resized_logo if img.mode == 'RGBA' else None)
    
    # Guardar en certificados/logo_play_store.png
    os.makedirs("certificados", exist_ok=True)
    play_store_img.save("certificados/logo_play_store.png", "PNG")
    print("-> Guardado icono para Google Play Store en 'certificados/logo_play_store.png' (512x512)")

    # 3. Generar Mipmaps para la aplicación Android
    res_base = os.path.join("app", "src", "main", "res")
    
    # Tamaños estándar de mipmaps
    mipmap_sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192
    }

    for folder, size in mipmap_sizes.items():
        folder_path = os.path.join(res_base, folder)
        os.makedirs(folder_path, exist_ok=True)
        
        # A) Icono Cuadrado estándar
        icon_canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        # Ajustamos el tamaño del logo escalándolo al 80% para que tenga buen margen en el launcher
        icon_w = int(width * (size * 0.8 / max(width, height)))
        icon_h = int(height * (size * 0.8 / max(width, height)))
        resized_icon = img.resize((icon_w, icon_h), Image.Resampling.LANCZOS)
        
        ix = (size - icon_w) // 2
        iy = (size - icon_h) // 2
        icon_canvas.paste(resized_icon, (ix, iy), resized_icon if img.mode == 'RGBA' else None)
        
        icon_canvas.save(os.path.join(folder_path, "ic_launcher.png"), "PNG")
        
        # B) Icono Redondo (con máscara de círculo)
        round_canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        # Para el redondo, podemos usar la misma imagen pero enmascarada
        mask = make_circle_mask((size, size))
        
        # Si el fondo original es opaco (como el círculo azul oscuro del usuario), 
        # recortamos la imagen final en forma de círculo.
        # Creamos una versión de fondo sólido para el círculo
        temp_round = Image.new("RGBA", (size, size), bg_color)
        temp_round.paste(resized_icon, (ix, iy), resized_icon if img.mode == 'RGBA' else None)
        
        # Aplicamos la máscara redonda
        round_canvas.paste(temp_round, (0, 0), mask)
        round_canvas.save(os.path.join(folder_path, "ic_launcher_round.png"), "PNG")
        
    print(f"-> Generados todos los iconos mipmap en '{res_base}'")
    return True

if __name__ == "__main__":
    process()
