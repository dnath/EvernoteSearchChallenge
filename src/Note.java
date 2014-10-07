import java.util.HashSet;
import java.util.Date;

public class Note implements Comparable<Note> {
    String guid;
    Date created;
    HashSet<String> tags;
    HashSet<String> words;

    Note(String guid, Date created, HashSet<String> tags, HashSet<String> words) {
        this.guid = guid;
        this.created = created;
        this.tags = tags;
        this.words = words;
    }

    public int compareTo(Note note) {
        int val = this.created.compareTo(note.created);
        if (val == 0) {
            val = this.guid.compareTo(note.guid);
        }
        return val;
    }

    public String toString() {
        return "guid: " + guid + "\tcreated: " + created;
    }
}