import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Groups files by hash. Any group with more than one file is a set of duplicates. */
public class DuplicateFinder {

    public List<List<FileInfo>> findDuplicates(List<FileInfo> files) {
        Map<String, List<FileInfo>> byHash = new LinkedHashMap<>();
        for (FileInfo file : files) {
            byHash.computeIfAbsent(file.hash(), h -> new ArrayList<>()).add(file);
        }

        List<List<FileInfo>> duplicates = new ArrayList<>();
        for (List<FileInfo> group : byHash.values()) {
            if (group.size() > 1) {
                duplicates.add(group);
            }
        }
        return duplicates;
    }
}
