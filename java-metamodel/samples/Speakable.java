/**
 * Interface representing an entity that can speak.
 */
public interface Speakable {

    /**
     * Make a sound.
     * @return the sound as a String
     */
    String speak();

    /**
     * Check if the entity is currently silent.
     */
    default boolean isSilent() {
        return false;
    }
}
