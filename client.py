import requests
import json
import os  # Añadir importación para variables de entorno

BASE_URL = "http://localhost:7000/api"
jwt_token = None

# Función nueva para mostrar la URL de MongoDB
def get_mongodb_url():
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

def register_user(username, password):
    """Registers a new user."""
    url = f"{BASE_URL}/auth/register"
    payload = {"username": username, "password": password}
    try:
        response = requests.post(url, json=payload)
        response.raise_for_status()  # Raise an exception for bad status codes (4xx or 5xx)
        print("Registration successful:", response.json())
        return True
    except requests.exceptions.RequestException as e:
        print(f"Registration failed: {e}")
        if e.response is not None:
            try:
                print("Server response:", e.response.json())
            except json.JSONDecodeError:
                print("Server response (non-JSON):", e.response.text)
        return False

def login_user(username, password):
    """Logs in a user and stores the JWT."""
    global jwt_token
    url = f"{BASE_URL}/auth/login"
    payload = {"username": username, "password": password}
    try:
        response = requests.post(url, json=payload)
        response.raise_for_status()
        data = response.json()
        jwt_token = data.get("token")
        if jwt_token:
            print("Login successful. Token received.")
            # You might want to check data.get("isAdmin") here too
            return True
        else:
            print("Login failed: Token not found in response.")
            return False
    except requests.exceptions.RequestException as e:
        print(f"Login failed: {e}")
        if e.response is not None:
            try:
                print("Server response:", e.response.json())
            except json.JSONDecodeError:
                print("Server response (non-JSON):", e.response.text)
        return False

def shorten_url(original_url):
    """Shortens a URL using the stored JWT."""
    if not jwt_token:
        print("Error: You must be logged in to shorten URLs.")
        return None

    url = f"{BASE_URL}/urls/shorten"
    payload = {"originalUrl": original_url}
    headers = {"Authorization": f"Bearer {jwt_token}"} # Include the JWT
    try:
        response = requests.post(url, json=payload, headers=headers)
        response.raise_for_status()
        data = response.json()
        print("URL shortened successfully:")
        print(f"  Short Code: {data.get('shortCode')}")
        print(f"  Original URL: {data.get('originalUrl')}")
        print(f"  Full Short URL: http://localhost:7000/s/{data.get('shortCode')}")
        return data
    except requests.exceptions.RequestException as e:
        print(f"URL shortening failed: {e}")
        if e.response is not None:
            try:
                print("Server response:", e.response.json())
            except json.JSONDecodeError:
                print("Server response (non-JSON):", e.response.text)
        return None

def get_user_urls():
    """Gets the list of URLs for the logged-in user."""
    if not jwt_token:
        print("Error: You must be logged in to view your URLs.")
        return None

    url = f"{BASE_URL}/urls/user"
    headers = {"Authorization": f"Bearer {jwt_token}"} # Include the JWT
    try:
        response = requests.get(url, headers=headers)
        response.raise_for_status()
        urls = response.json()
        print("\nYour Shortened URLs:")
        if not urls:
            print("  No URLs found.")
        else:
            for item in urls:
                print(f"  - Short Code: {item.get('shortCode')}, Original: {item.get('originalUrl')}, Clicks: {item.get('accessCount', 0)}")
        return urls
    except requests.exceptions.RequestException as e:
        print(f"Failed to get user URLs: {e}")
        if e.response is not None:
            try:
                print("Server response:", e.response.json())
            except json.JSONDecodeError:
                print("Server response (non-JSON):", e.response.text)
        return None


# --- Example Usage ---
if __name__ == "__main__":
    print("--- URL Shortener Client ---")
    
    # Mostrar URL de MongoDB
    print(f"MongoDB URL: {get_mongodb_url()}")
    print("-" * 40)

    username = "test"
    password = "test"
    print(f"Iniciando sesión como {username}...")
    
    if login_user(username, password):
        print("\n=== Obteniendo URLs existentes ===")
        # Obtener las URLs actuales del usuario
        urls = get_user_urls()
        
        # Solo crear nuevas URLs si el usuario no tiene ninguna
        if not urls or len(urls) == 0:
            print("\n=== No se encontraron URLs, creando ejemplos ===")
            shorten_url("https://www.example.com/a-very-long-url-that-needs-shortening")
            shorten_url("google.com")
            
            # Actualizar la lista de URLs
            print("\n=== URLs después de crear ejemplos ===")
            get_user_urls()
    else:
        print("\nCannot perform further actions without logging in.")
