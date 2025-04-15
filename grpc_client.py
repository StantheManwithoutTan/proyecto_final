import grpc
import url_shortener_pb2 as pb2
import url_shortener_pb2_grpc as pb2_grpc
import base64
from datetime import datetime
from PIL import Image
import io
import sys
import os  # Añadir importación para variables de entorno

class URLShortenerClient:
    def __init__(self, server_address='localhost:50051'):
        """Inicializa el cliente gRPC."""
        self.channel = grpc.insecure_channel(server_address)
        self.stub = pb2_grpc.URLShortenerServiceStub(self.channel)
        self.token = None
    
    def login(self, username, password):
        """Autentica al usuario y almacena el token."""
        try:
            request = pb2.LoginRequest(username=username, password=password)
            response = self.stub.Login(request)
            
            if response.success:
                self.token = response.token
                print(f"Login exitoso como {username}")
                print(f"Rol: {'Administrador' if response.is_admin else 'Usuario regular'}")
                return True
            else:
                print(f"Error de autenticación: {response.message}")
                return False
        except grpc.RpcError as e:
            print(f"Error de conexión: {e.details()}")
            return False
    
    def get_user_urls(self):
        """Lista las URLs del usuario con sus estadísticas."""
        if not self.token:
            print("Error: Debes iniciar sesión primero.")
            return None
        
        try:
            request = pb2.UserRequest(token=self.token)
            response = self.stub.GetUserUrls(request)
            
            print(f"\n=== URLs acortadas ({len(response.urls)}) ===")
            
            urls_list = []
            for url in response.urls:
                print(f"- Short Code: {url.short_code}")
                print(f"  URL Original: {url.original_url}")
                print(f"  Creada: {url.created_at}")
                print(f"  Visitas: {url.access_count}")
                print("")
                urls_list.append(url)
            
            return urls_list
        
        except grpc.RpcError as e:
            print(f"Error al obtener URLs: {e.details()}")
            return None
    
    def create_short_url(self, original_url):
        """Crea una URL acortada con vista previa en base64."""
        if not self.token:
            print("Error: Debes iniciar sesión primero.")
            return None
        
        try:
            request = pb2.CreateUrlRequest(token=self.token, original_url=original_url)
            response = self.stub.CreateShortUrl(request)
            
            print("\n=== URL acortada exitosamente ===")
            print(f"URL Original: {response.original_url}")
            print(f"URL Acortada: {response.full_short_url}")
            print(f"Creada: {response.created_at}")
            
            # Si hay vista previa, guardarla como imagen
            if response.preview_image_base64:
                try:
                    # Decodificar la imagen base64
                    image_data = base64.b64decode(response.preview_image_base64)
                    
                    # Crear un archivo temporal para la imagen
                    temp_file = f"preview_{response.short_code}.png"
                    with open(temp_file, "wb") as f:
                        f.write(image_data)
                    
                    print(f"Vista previa guardada en: {temp_file}")
                    
                    # Opcionalmente, mostrar la imagen
                    try:
                        img = Image.open(io.BytesIO(image_data))
                        img.show()
                    except Exception as img_error:
                        print(f"No se pudo mostrar la imagen: {img_error}")
                    
                except Exception as e:
                    print(f"Error al procesar la vista previa: {e}")
            else:
                print("No hay vista previa disponible")
            
            return response
        
        except grpc.RpcError as e:
            print(f"Error al crear URL acortada: {e.details()}")
            return None
    
    def get_url_analytics(self, short_code):
        """Obtiene estadísticas detalladas de una URL."""
        if not self.token:
            print("Error: Debes iniciar sesión primero.")
            return None
        
        try:
            request = pb2.UrlAnalyticsRequest(token=self.token, short_code=short_code)
            response = self.stub.GetUrlAnalytics(request)
            
            print(f"\n=== Estadísticas para {response.short_code} ===")
            print(f"URL Original: {response.original_url}")
            print(f"Visitas Totales: {response.access_count}")
            
            # Mostrar estadísticas de navegadores
            print("\nNavegadores:")
            for browser, count in response.browsers.items():
                print(f"- {browser}: {count}")
            
            # Mostrar estadísticas de sistemas operativos
            print("\nSistemas Operativos:")
            for os, count in response.operating_systems.items():
                print(f"- {os}: {count}")
            
            # Mostrar últimos accesos
            print("\nÚltimos accesos:")
            for access in response.accesses[:5]:  # Mostrar solo los primeros 5
                print(f"- {access.timestamp}: desde {access.ip} usando {access.browser} en {access.os}")
            
            return response
        
        except grpc.RpcError as e:
            print(f"Error al obtener estadísticas: {e.details()}")
            return None
    
    def get_mongodb_url(self):
        """Retrieves and returns the MongoDB connection URL."""
        mongodb_url = os.getenv("URL_MONGO")
        if mongodb_url:
            # Ocultar contraseña para mostrar (opcional)
            safe_url = mongodb_url
            if "@" in mongodb_url:
                parts = mongodb_url.split("@")
                credentials = parts[0].split("://")[1]
                user = credentials.split(":")[0]
                safe_url = mongodb_url.replace(credentials, f"{user}:****")
            return safe_url
        else:
            return "No configurada. Por favor, establece la variable de entorno URL_MONGO."
    
    def close(self):
        """Cierra el canal gRPC."""
        if self.channel:
            self.channel.close()
            print("Conexión gRPC cerrada")


if __name__ == "__main__":
    client = URLShortenerClient()
    print("==== Cliente gRPC para URL Shortener ====")
    
    # Mostrar URL de MongoDB
    print(f"MongoDB URL: {client.get_mongodb_url()}")
    print("-" * 40)
    
    username = "test"
    password = "test"
    print(f"Iniciando sesión como {username}...")
    
    if client.login(username, password):
        print("\n=== Obteniendo URLs existentes ===")
        # Obtener las URLs actuales del usuario
        urls = client.get_user_urls()
        
        # Solo crear nuevas URLs si el usuario no tiene ninguna o tiene pocas
        if not urls or len(urls) == 0:
            print("\n=== No se encontraron URLs, creando ejemplos ===")
            client.create_short_url("https://en.wikipedia.org/wiki/Doom_(2016_video_game)")
            client.create_short_url("https://github.com/features/copilot")
            
            # Actualizar la lista de URLs
            print("\n=== URLs después de crear ejemplos ===")
            urls = client.get_user_urls()
        
        # Obtener estadísticas de la primera URL si hay alguna
        if urls and len(urls) > 0:
            print("\n=== Estadísticas detalladas para la primera URL ===")
            client.get_url_analytics(urls[0].short_code)
    
    print("\n==== Sesión finalizada ====")
    client.close()