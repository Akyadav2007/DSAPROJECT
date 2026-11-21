
import java.util.*;
// ---------- Model classes ----------

class MenuItem {

    int id;
    String name;
    String category;
    double price;
    double rating;

    MenuItem(int id, String name, String category, double price, double rating) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.rating = rating;
    }

    @Override
    public String toString() {
        return String.format("%-4d %-22s %-12s Rs.%-8.2f %.1f*", id, name, category, price, rating);
    }
}

class CartItem {

    MenuItem item;
    int qty;

    CartItem(MenuItem item, int qty) {
        this.item = item;
        this.qty = qty;
    }

    double total() {
        return item.price * qty;
    }
}

class Order {

    private static int counter = 1000;
    int orderId;
    String customer;
    ArrayList<CartItem> items;
    double total;
    String status;

    Order(String customer, ArrayList<CartItem> cartSnapshot) {
        this.orderId = ++counter;
        this.customer = customer;
        this.items = cartSnapshot;
        this.total = 0;
        for (CartItem c : items) {
            total += c.total();
        }
        this.status = "PLACED";
    }

    void print() {
        System.out.println("Order #" + orderId + " | " + customer + " | " + status);
        for (CartItem c : items) {
            System.out.printf("   %-22s x%-3d Rs.%.2f%n", c.item.name, c.qty, c.total());
        }
        System.out.printf("   TOTAL: Rs.%.2f%n", total);
    }
}

// Records a cart action so it can be undone using a Stack
class CartAction {

    enum Type {
        ADD, REMOVE
    }
    Type type;
    MenuItem item;
    int qty;

    CartAction(Type type, MenuItem item, int qty) {
        this.type = type;
        this.item = item;
        this.qty = qty;
    }
}

// ---------- Main system ----------
public class FoodOrderingSystem {

    private final ArrayList<MenuItem> menu = new ArrayList<>();
    private final ArrayList<CartItem> cart = new ArrayList<>();
    private final Stack<CartAction> undoStack = new Stack<>();
    private final Queue<Order> pendingOrders = new LinkedList<>();
    private final Stack<Order> orderHistory = new Stack<>();
    private final Scanner sc = new Scanner(System.in);
    private String customerName = "Guest";

    public FoodOrderingSystem() {
        menu.add(new MenuItem(1, "Paneer Butter Masala", "Main Course", 220, 4.5));
        menu.add(new MenuItem(2, "Veg Biryani", "Main Course", 180, 4.2));
        menu.add(new MenuItem(3, "Butter Naan", "Bread", 35, 4.0));
        menu.add(new MenuItem(4, "Masala Dosa", "South Indian", 90, 4.6));
        menu.add(new MenuItem(5, "Veg Burger", "Fast Food", 120, 3.9));
        menu.add(new MenuItem(6, "French Fries", "Fast Food", 80, 4.1));
        menu.add(new MenuItem(7, "Gulab Jamun", "Dessert", 60, 4.7));
        menu.add(new MenuItem(8, "Cold Coffee", "Beverage", 70, 4.3));
        menu.add(new MenuItem(9, "Masala Chai", "Beverage", 20, 4.4));
    }

    // ---------- Menu & sorting ----------
    private void showMenu(List<MenuItem> list) {
        System.out.println("\n" + String.format("%-4s %-22s %-12s %-11s %s", "ID", "Name", "Category", "Price", "Rating"));
        System.out.println("-".repeat(62));
        if (list.isEmpty()) {
            System.out.println("No items found.");
        }
        for (MenuItem m : list) {
            System.out.println(m);
        }
    }

    // Manual insertion sort (demonstrates the algorithm) - used for price
    private void insertionSortByPrice(ArrayList<MenuItem> list, boolean ascending) {
        for (int i = 1; i < list.size(); i++) {
            MenuItem key = list.get(i);
            int j = i - 1;
            while (j >= 0 && (ascending ? list.get(j).price > key.price : list.get(j).price < key.price)) {
                list.set(j + 1, list.get(j));
                j--;
            }
            list.set(j + 1, key);
        }
    }

    private void sortMenu() {
        System.out.println("\nSort by: 1) Price Low->High  2) Price High->Low  3) Name A-Z  4) Rating High->Low");
        int ch = readInt("Choice: ");
        ArrayList<MenuItem> copy = new ArrayList<>(menu); // keep original order intact
        switch (ch) {
            case 1 ->
                insertionSortByPrice(copy, true);
            case 2 ->
                insertionSortByPrice(copy, false);
            case 3 ->
                copy.sort(Comparator.comparing(m -> m.name.toLowerCase()));
            case 4 ->
                copy.sort((a, b) -> Double.compare(b.rating, a.rating));
            default -> {
                System.out.println("Invalid choice.");
                return;
            }
        }
        showMenu(copy);
    }

    private void filterByCategory() {
        System.out.print("Enter category (e.g. Fast Food, Dessert, Beverage): ");
        String cat = sc.nextLine().trim();
        ArrayList<MenuItem> result = new ArrayList<>();
        for (MenuItem m : menu) {
            if (m.category.equalsIgnoreCase(cat)) {
                result.add(m);
            }
        }
        showMenu(result);
    }

    private MenuItem findById(int id) {
        for (MenuItem m : menu) {
            if (m.id == id) {
                return m;
            }
        }
        return null;
    }

    // ---------- Cart ----------
    private CartItem findInCart(int id) {
        for (CartItem c : cart) {
            if (c.item.id == id) {
                return c;
            }
        }
        return null;
    }

    private void addToCartInternal(MenuItem item, int qty) {
        CartItem existing = findInCart(item.id);
        if (existing != null) {
            existing.qty += qty;
        } else {
            cart.add(new CartItem(item, qty));
        }
    }

    private void removeFromCartInternal(MenuItem item, int qty) {
        CartItem existing = findInCart(item.id);
        if (existing == null) {
            return;
        }
        existing.qty -= qty;
        if (existing.qty <= 0) {
            cart.remove(existing);
        }
    }

    private void addToCart() {
        int id = readInt("Enter item ID: ");
        MenuItem item = findById(id);
        if (item == null) {
            System.out.println("Item not found.");
            return;
        }
        int qty = readInt("Quantity: ");
        if (qty <= 0) {
            System.out.println("Quantity must be positive.");
            return;
        }
        addToCartInternal(item, qty);
        undoStack.push(new CartAction(CartAction.Type.ADD, item, qty));
        System.out.println(qty + " x " + item.name + " added to cart.");
    }

    private void removeFromCart() {
        int id = readInt("Enter item ID to remove: ");
        CartItem ci = findInCart(id);
        if (ci == null) {
            System.out.println("Item is not in your cart.");
            return;
        }
        int qty = readInt("Quantity to remove (max " + ci.qty + "): ");
        if (qty <= 0 || qty > ci.qty) {
            System.out.println("Invalid quantity.");
            return;
        }
        MenuItem item = ci.item;
        removeFromCartInternal(item, qty);
        undoStack.push(new CartAction(CartAction.Type.REMOVE, item, qty));
        System.out.println("Removed " + qty + " x " + item.name);
    }

    private void undoLastAction() {
        if (undoStack.isEmpty()) {
            System.out.println("Nothing to undo.");
            return;
        }
        CartAction a = undoStack.pop(); // LIFO
        if (a.type == CartAction.Type.ADD) {
            removeFromCartInternal(a.item, a.qty);
            System.out.println("Undone: addition of " + a.qty + " x " + a.item.name);
        } else {
            addToCartInternal(a.item, a.qty);
            System.out.println("Undone: removal of " + a.qty + " x " + a.item.name);
        }
    }

    private double cartTotal() {
        double t = 0;
        for (CartItem c : cart) {
            t += c.total();
        }
        return t;
    }

    private void viewCart() {
        if (cart.isEmpty()) {
            System.out.println("\nYour cart is empty.");
            return;
        }
        System.out.println("\n--- Your Cart ---");
        for (CartItem c : cart) {
            System.out.printf("%-22s x%-3d Rs.%.2f%n", c.item.name, c.qty, c.total());
        }
        System.out.printf("Total: Rs.%.2f%n", cartTotal());
    }

    // ---------- Orders ----------
    private void placeOrder() {
        if (cart.isEmpty()) {
            System.out.println("Cart is empty. Add items first.");
            return;
        }
        viewCart();
        System.out.print("Confirm order? (y/n): ");
        if (!sc.nextLine().trim().equalsIgnoreCase("y")) {
            System.out.println("Order cancelled.");
            return;
        }

        Order order = new Order(customerName, new ArrayList<>(cart)); // snapshot of cart
        pendingOrders.offer(order);                                   // enqueue
        cart.clear();
        undoStack.clear();
        System.out.println("Order placed! Your Order ID: " + order.orderId
                + " | Orders ahead of you: " + (pendingOrders.size() - 1));
    }

    // Kitchen / admin action
    private void processNextOrder() {
        Order o = pendingOrders.poll(); // dequeue - FIFO
        if (o == null) {
            System.out.println("No pending orders.");
            return;
        }
        o.status = "DELIVERED";
        orderHistory.push(o);           // push onto history stack
        System.out.println("Processed and delivered:");
        o.print();
    }

    private void viewPendingOrders() {
        if (pendingOrders.isEmpty()) {
            System.out.println("No pending orders.");
            return;
        }
        System.out.println("\n--- Pending Orders (in serving order) ---");
        int pos = 1;
        for (Order o : pendingOrders) {
            System.out.printf("%d) Order #%d | %s | Rs.%.2f%n", pos++, o.orderId, o.customer, o.total);
        }
    }

    private void viewOrderHistory() {
        if (orderHistory.isEmpty()) {
            System.out.println("No completed orders yet.");
            return;
        }
        System.out.println("\n--- Order History (latest first) ---");
        for (int i = orderHistory.size() - 1; i >= 0; i--) {
            orderHistory.get(i).print();
        }
    }

    // ---------- Admin ----------
    private void addMenuItem() {
        System.out.print("Name: ");
        String name = sc.nextLine().trim();
        System.out.print("Category: ");
        String cat = sc.nextLine().trim();
        double price = readDouble("Price: ");
        double rating = readDouble("Rating (0-5): ");
        int newId = menu.isEmpty() ? 1 : menu.get(menu.size() - 1).id + 1;
        menu.add(new MenuItem(newId, name, cat, price, rating));
        System.out.println("Menu item added with ID " + newId);
    }

    // ---------- Input helpers ----------
    private int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    private double readDouble(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Double.parseDouble(sc.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    // ---------- Menu loop ----------
    public void run() {
        System.out.println("=== Welcome to the Online Food Ordering System ===");
        System.out.print("Enter your name: ");
        String n = sc.nextLine().trim();
        if (!n.isEmpty()) {
            customerName = n;
        }

        while (true) {
            System.out.println("\n========== MAIN MENU ==========");
            System.out.println(" 1. View menu");
            System.out.println(" 2. Sort menu");
            System.out.println(" 3. Filter by category");
            System.out.println(" 4. Add item to cart");
            System.out.println(" 5. Remove item from cart");
            System.out.println(" 6. Undo last cart action");
            System.out.println(" 7. View cart");
            System.out.println(" 8. Place order");
            System.out.println(" 9. View my order history");
            System.out.println("--------- Restaurant / Admin ---------");
            System.out.println("10. View pending orders (queue)");
            System.out.println("11. Process next order");
            System.out.println("12. Add new menu item");
            System.out.println(" 0. Exit");
            int ch = readInt("Choice: ");
            switch (ch) {
                case 1 ->
                    showMenu(menu);
                case 2 ->
                    sortMenu();
                case 3 ->
                    filterByCategory();
                case 4 ->
                    addToCart();
                case 5 ->
                    removeFromCart();
                case 6 ->
                    undoLastAction();
                case 7 ->
                    viewCart();
                case 8 ->
                    placeOrder();
                case 9 ->
                    viewOrderHistory();
                case 10 ->
                    viewPendingOrders();
                case 11 ->
                    processNextOrder();
                case 12 ->
                    addMenuItem();
                case 0 -> {
                    System.out.println("Thank you! Goodbye.");
                    return;
                }
                default ->
                    System.out.println("Invalid choice. Try again.");
            }
        }
    }

    public static void main(String[] args) {
        new FoodOrderingSystem().run();
    }
}
