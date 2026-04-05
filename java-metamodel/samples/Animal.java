/**
 * Abstract base class representing an animal.
 * Demonstrates abstract class with fields, constructor, and methods.
 */
public abstract class Animal implements Speakable {

    private String name;
    protected int age;
    private static int count = 0;

    /**
     * Create a new Animal.
     * @param name the animal's name
     * @param age  the animal's age
     */
    public Animal(String name, int age) {
        this.name = name;
        this.age = age;
        count++;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public static int getCount() {
        return count;
    }

    /**
     * Describe this animal.
     * @return description string
     */
    public abstract String describe();

    @Override
    public String toString() {
        return name + " (age " + age + ")";
    }
}
