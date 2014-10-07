import java.io.FileInputStream;
import java.lang.String;
import java.lang.System;
import java.lang.Throwable;
import java.util.HashMap;
import java.util.Scanner;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.SortedMap;
import java.util.TreeMap;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Iterator;

public class EvernoteSearch {
    final static String CREATED_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    final static String NOTE_DELIM = "</note>";
    final static TimeZone UTC_TIME_ZONE = TimeZone.getTimeZone("UTC");
    final static SimpleDateFormat CREATED_SDF = new SimpleDateFormat(CREATED_FORMAT);

    final static Pattern CREATED_PATTERN = Pattern.compile("<created>(.*?)</created>", Pattern.DOTALL);
    final static Pattern GUID_PATTERN = Pattern.compile("<guid>(.*?)</guid>", Pattern.DOTALL);
    final static Pattern TAG_PATTERN = Pattern.compile("<tag>(.*?)</tag>", Pattern.DOTALL);
    final static Pattern CONTENT_PATTERN = Pattern.compile("<content>(.*?)</content>", Pattern.DOTALL);

    Scanner scanner;
    HashMap<String, Note> corpus;
    TreeSet<Note> deletedNotes;

    // inverted indices
    TreeMap<Date, TreeSet<Note>> createdDateIndex;
    TreeMap<String, TreeSet<Note>> tagIndex;
    TreeMap<String, TreeSet<Note>> contentIndex;

    public EvernoteSearch(String inputFilename) throws Throwable {
        CREATED_SDF.setTimeZone(UTC_TIME_ZONE);

        if (inputFilename == null) {
            scanner = new Scanner(System.in);
        }
        else {
            scanner = new Scanner(new FileInputStream(inputFilename));
        }

        corpus = new HashMap<String, Note>();
        deletedNotes = new TreeSet<Note>();

        createdDateIndex = new TreeMap<Date, TreeSet<Note>>();
        contentIndex = new TreeMap<String, TreeSet<Note>>();
        tagIndex = new TreeMap<String, TreeSet<Note>>();
    }


    TreeSet<Note> search(String searchString) {
        TreeSet<Note> notes = null;
        boolean started = false;

        String[] searchWords = searchString.split("[\\s]");
        for (String searchWord : searchWords) {
            TreeSet<Note> tmp = null;

            if (searchWord.startsWith("tag:")) {
                String tag = searchWord.substring(4);
//                System.err.println("search tag = " + tag);

                if (tag.endsWith("*")) {
                    String from = tag.substring(0, searchWord.length() - 1);

                    StringBuffer tmpBuffer = new StringBuffer(from);
                    tmpBuffer.setCharAt(from.length() - 1, (char)(from.charAt(from.length() - 1) + 1));
                    String to = tmpBuffer.toString();

                    SortedMap<String, TreeSet<Note>> res = tagIndex.subMap(from, true, to, false);
                    for(Map.Entry<String, TreeSet<Note>> entry : res.entrySet()) {
                        TreeSet<Note> noteSet = entry.getValue();
                        if (tmp == null) {
                            tmp = new TreeSet<Note>();
                        }
                        tmp.addAll(noteSet);
                    }
                }
                else {
                    tmp = tagIndex.get(tag);
                }
            }
            else if (searchWord.startsWith("created:")) {
                String createdString = searchWord.substring(8);

                int year = Integer.parseInt(createdString.substring(0,4)) - 1900;
                int month = Integer.parseInt(createdString.substring(4,6)) - 1;
                int day = Integer.parseInt(createdString.substring(6,8)) - 1;
                Date created = new Date(year, month, day);

//                System.err.println("search created = " + created);

                SortedMap<Date, TreeSet<Note>> createdTailMap = createdDateIndex.tailMap(created);

                for(Map.Entry<Date, TreeSet<Note>> entry : createdTailMap.entrySet()) {
                    TreeSet<Note> noteSet = entry.getValue();
                    if (tmp == null) {
                        tmp = new TreeSet<Note>();
                    }
                    tmp.addAll(noteSet);
                }
            }
            else {
//                System.err.println("search word = " + searchWord);

                if (searchWord.endsWith("*")) {
                    String from = searchWord.substring(0, searchWord.length() - 1);

                    StringBuffer tmpBuffer = new StringBuffer(from);
                    tmpBuffer.setCharAt(from.length() - 1, (char) (from.charAt(from.length() - 1) + 1));
                    String to = tmpBuffer.toString();

//                    System.out.println("\n\n\nfrom = " + from + "\tto = " + to);

                    SortedMap<String, TreeSet<Note>> res = contentIndex.subMap(from, true, to, false);
                    for(Map.Entry<String, TreeSet<Note>> entry : res.entrySet()) {
                        TreeSet<Note> noteSet = entry.getValue();
                        if (tmp == null) {
                            tmp = new TreeSet<Note>();
                        }
                        tmp.addAll(noteSet);
                    }
                }
                else {
                    tmp = contentIndex.get(searchWord);
                }
            }

            if (tmp == null || tmp.size() == 0) {
                return null;
            }

            if (started == false) {
                notes = (TreeSet<Note>)tmp.clone();
            }
            else {
                notes.retainAll(tmp);
                if (notes == null || notes.size() == 0) {
                    return null;
                }
            }

            started = true;
        }

        if (notes != null) {
            notes.removeAll(deletedNotes);
        }

        return notes;
    }

    Note readNote() throws Throwable {
        scanner.useDelimiter(NOTE_DELIM);
        String noteXMLString = scanner.next();
        scanner.reset();
        scanner.next();

        // System.out.println("***\n" + noteXMLString + "\n***");

        String guid = getGUID(noteXMLString);
        Date created = getCreatedDate(noteXMLString);
        HashSet<String> tags = getTags(noteXMLString);
        HashSet<String> words = getWords(noteXMLString);

        Note note = new Note(guid, created, tags, words);

        // System.err.println(note);

        return note;
    }

    private void updateIndices(Note note) {
//        System.out.println("***" + note.guid);

        HashSet<String> tags = note.tags;
        HashSet<String> words = note.words;

        // createdDateIndex
        addNoteToCreatedDateIndex(note);

        //        printCreatedDateIndex();

        Iterator<String> tagsIterator = tags.iterator();
        while (tagsIterator.hasNext()) {
            String tag = tagsIterator.next();
            TreeSet<Note> notes = null;
            notes = tagIndex.get(tag);
            if (notes == null) {
                notes = new TreeSet<Note>();
                notes.add(note);
                tagIndex.put(tag, notes);
            }
            else {
                notes.add(note);
            }
        }

//        printTagIndex();

        Iterator<String> wordsIterator = words.iterator();
        while (wordsIterator.hasNext()) {
            String word = wordsIterator.next();

//            System.out.println(word);

            TreeSet<Note> notes = null;
            notes = contentIndex.get(word);
            if (notes == null) {
                notes = new TreeSet<Note>();
                notes.add(note);
                contentIndex.put(word, notes);
            }
            else {
                notes.add(note);
            }
        }

//        printContentIndex();
    }

    private HashSet<String> getTags(String noteXMLString) {
        HashSet<String> tags = new HashSet<String>();
        Matcher matcher = TAG_PATTERN.matcher(noteXMLString);
        while (matcher.find()) {
            String tag = matcher.group(1).trim().toLowerCase();
            if (!tag.equals("")) {
                tags.add(tag);
            }
            // System.err.println("tag = " + tag);
        }

        return tags;
    }

    private HashSet<String> getWords(String noteXMLString) {
        HashSet<String> words = new HashSet<String>();
        Matcher matcher = CONTENT_PATTERN.matcher(noteXMLString);

        if (matcher.find()) {
            String content = matcher.group(1).trim().toLowerCase();
            // System.err.println("content = " + content);

            String[] list = content.split("[^a-zA-Z0-9_'&]");
            for (String word : list) {
                if (!word.trim().equals("") && words.contains(word) == false) {
                    words.add(word);
                }
            }
        }
        else {
            System.err.println("No content!");
        }

        return words;
    }

    private Date getCreatedDate(String noteXMLString) throws Throwable {
        Date created = null;
        Matcher matcher = CREATED_PATTERN.matcher(noteXMLString);

        if (matcher.find()) {
            String createdString = matcher.group(1);
//            System.err.println("createdString = " + createdString);

            created = CREATED_SDF.parse(createdString);
//            System.err.println("created = " + created);
        }
        else {
            System.err.println("No created date!");
        }

        return created;
    }

    private String getGUID(String noteXMLString) {
        String guid = null;
        Matcher matcher = GUID_PATTERN.matcher(noteXMLString);

        if (matcher.find()) {
            guid = matcher.group(1).trim();
            // System.err.println("guid = " + guid);
        }
        else {
            System.err.println("No guid!");
        }

        return guid;
    }

    public void run() {
        while (scanner.hasNext()) {
            String command = scanner.next();

//            System.err.println("command = " + command);

            if (command.equals("CREATE")) {
                try {
                    Note note = readNote();
                    corpus.put(note.guid, note);
                    updateIndices(note);

//                    System.err.println("corpus size  = " + corpus.size());
//                    printTagIndex();
//                    printCreatedDateIndex();

                } catch(Throwable t) {
                    t.printStackTrace();
                }
            }
            else if (command.equals("UPDATE")) {
                try {
                    Note note = readNote();
                    String guid = note.guid;

                    Note oldNote = corpus.get(guid);

                    corpus.remove(oldNote);
                    corpus.put(guid, note);

                    createdDateIndex.get(note.created).remove(oldNote);

                    for (String tag : oldNote.tags) {
                        tagIndex.get(tag).remove(oldNote);
                    }

                    for (String word : oldNote.words) {
                        contentIndex.get(word).remove(oldNote);
                    }

                    updateIndices(note);

                } catch(Throwable t) {
                    t.printStackTrace();
                }
            }
            else if (command.equals("DELETE")) {
                String guid = scanner.next().trim();
//                System.err.println("delete guid = " + guid);
                deletedNotes.add(corpus.get(guid));
            }
            else if (command.equals("SEARCH")) {
                scanner.useDelimiter("\n");
                String searchString = scanner.next();
                scanner.reset();

//                System.out.println("searchString = " + searchString);
                TreeSet<Note> results = search(searchString);

//                System.err.println("results size = " + results.size());
                if (results == null || results.size() == 0) {
                    System.out.println();
                }
                else {
                    Iterator<Note> iter = results.iterator();
                    System.out.print(iter.next().guid);
                    while (iter.hasNext()) {
                        System.out.print("," + iter.next().guid);
                    }
                    System.out.println();
                }
            }
            else {
                System.err.println("Invalid Command!");
            }
        }
    }

    void addNoteToCreatedDateIndex(Note note) {
        TreeSet<Note> notes = null;
        notes = createdDateIndex.get(note.created);
        if (notes == null) {
            notes = new TreeSet<Note>();
            notes.add(note);
            createdDateIndex.put(note.created, notes);
        }
        else {
            notes.add(note);
        }
    }

    private void printTagIndex() {
        System.err.println("tagIndex = \n\n");
        for(Map.Entry<String, TreeSet<Note>> entry : tagIndex.entrySet()) {
            String key = entry.getKey();
            TreeSet<Note> notes = entry.getValue();

            System.err.println(key + " => [");
            for (Note note : notes) {
                System.err.println("\t" + note.guid);
            }
            System.err.println("]");
            System.err.println();
        }
    }

    private void printContentIndex() {
        System.err.println("contentIndex = \n\n");
        for(Map.Entry<String, TreeSet<Note>> entry : contentIndex.entrySet()) {
            String key = entry.getKey();
            TreeSet<Note> notes = entry.getValue();

            System.err.println(key + " => [");
            for (Note note : notes) {
                System.err.println("\t" + note.guid);
            }
            System.err.println("]");
            System.err.println();
        }
    }

    private void printCreatedDateIndex() {
        System.err.println("createdDateIndex = \n\n");
        for(Map.Entry<Date, TreeSet<Note>> entry : createdDateIndex.entrySet()) {
            Date key = entry.getKey();
            TreeSet<Note> notes = entry.getValue();

            System.err.println(key + " => [");
            for (Note note : notes) {
                System.err.println("\t" + note.guid);
            }
            System.err.println("]");
            System.err.println();
        }
    }

    public static void main(String[] args) {
        try {
            String inputFilename = "input/input.txt";
            EvernoteSearch solution = new EvernoteSearch(inputFilename);
            solution.run();
        } catch(Throwable t) {
            t.printStackTrace();
        }
    }
}