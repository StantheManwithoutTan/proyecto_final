package proyecto_final.model;

public class User {
    private String username;
    private String password;
    private boolean isAdmin;
    private boolean isRootAdmin; // El administrador principal que nunca puede ser eliminado

    public User(String username, String password, boolean isAdmin, boolean isRootAdmin) {
        this.username = username;
        this.password = password;
        this.isAdmin = isAdmin;
        this.isRootAdmin = isRootAdmin;
    }

    // Constructor simplificado para usuarios regulares
    public User(String username, String password) {
        this(username, password, false, false);
    }

    // Getters y setters
    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }

    public boolean isRootAdmin() {
        return isRootAdmin;
    }
}