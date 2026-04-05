/**
 * Concrete class representing a Dog.
 * Extends Animal and implements Speakable (via Animal).
 * Demonstrates concrete class with overridden methods and additional fields.
 */
public class Dog extends Animal {

    private String breed;
    private boolean trained;

    public Dog(String name, int age, String breed) {
        super(name, age);
        this.breed = breed;
        this.trained = false;
    }

    public String getBreed() {
        return breed;
    }

    public boolean isTrained() {
        return trained;
    }

    public void train() {
        this.trained = true;
    }

    @Override
    public String speak() {
        return "Woof!";
    }

    @Override
    public String describe() {
        return getName() + " is a " + breed + " dog, age " + getAge();
    }

    /**
     * Fetch a ball — a Dog-specific behavior.
     * @param distance how far to throw
     * @return result message
     */
    public String fetch(int distance) {
        return getName() + " fetches a ball thrown " + distance + " meters!";
    }
}
