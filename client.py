import requests
import json

BASE_URL = "http://localhost:7000/api"  # Your API base URL
jwt_token = None

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

    # Example: Register a new user (optional)
    # register_user("testuser_py", "password123")

    # Example: Login
    if login_user("api", "api"): # Replace with a valid username/password
        # Example: Shorten a URL
        shorten_url("https://www.example.com/a-very-long-url-that-needs-shortening")
        shorten_url("google.com") # Example without http://

        # Example: List user's URLs
        get_user_urls()
    else:
        print("\nCannot perform further actions without logging in.")
