package players.rl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

class DataProcessor {

    // An entry for the database. Content defined in DataProcessor::formatData
    static enum Field {
        // The name of the game
        Game,
        // The seed used
        Seed,
        // The type of reinforcement learning done
        Type,
        // The alpha value used [0, 1]
        Alpha,
        // The gamma value used [0, 1]
        Gamma,
        // The epsilon value used [0, 1]
        Epsilon,
        // The solver used (Q-Learning, SARSA, etc.)
        Solver,
        // The class name of the used IStateHeuristic
        Heuristic,
        // The class name of the used players.rl.RLFeatureVector
        FeatureVector,
        // The total number of games played by this entry
        NGamesTotal,
        // The number of games played by this entry with these parameters & settings
        NGamesWithTheseSettings,
        // All data these weights were trained on previously
        ContinuedFrom;

        static Field[] getUniqueFields() {
            return Arrays.stream(Field.values())
                    .filter(field -> !Arrays.asList(getNonUniqueFields()).contains(field))
                    .toArray(Field[]::new);
        }

        static Field[] getNonUniqueFields() {
            return new Field[] { Field.NGamesTotal, Field.NGamesWithTheseSettings,
                    Field.ContinuedFrom };
        }
    }

    private QWeightsDataStructure qwds;

    private String dateTime = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
    private File outfile = null;
    private ObjectNode metadata = null;

    private int nGamesPlayedFromInfile;

    DataProcessor(QWeightsDataStructure qwds) {
        this.qwds = qwds;
        createMissingFoldersAndFiles();
        initMetadata();
    }

    private void updateSegmentOutfile(int nGames) {
        int totalNGames = nGamesPlayedFromInfile + nGames;
        String outfilePrefix = qwds.trainingParams.outfilePrefix != null ? qwds.trainingParams.outfilePrefix : dateTime;
        Path outfilePath = Paths.get(DataProcessor.getGameFolderPath(qwds.trainingParams.gameName).toString(),
                qwds.playerParams.getType().name() + "/" + outfilePrefix + "_n=" + totalNGames + ".json");
        outfile = outfilePath.toFile();
    }

    private void initMetadata() {
        ObjectNode existingMetadata = qwds.playerParams.infileNameOrPath == null ? null
                : (ObjectNode) readInputFile(qwds.trainingParams.gameName, qwds.playerParams.infileNameOrPath)
                        .get("Metadata");
        nGamesPlayedFromInfile = existingMetadata == null ? 0 : existingMetadata.get(Field.NGamesTotal.name()).asInt();

        ObjectMapper om = new ObjectMapper();

        // This makes sure to map all floats to doubles before writing, creating a
        // DoubleNode. Necessary for equality checks, since all read decimal point
        // numbers are read as DoubleNode and not as FloatNode.
        class DoubleJsonNodeFactory extends JsonNodeFactory {
            @Override
            public NumericNode numberNode(float v) {
                return numberNode((double) v);
            }
        }
        om.setNodeFactory(new DoubleJsonNodeFactory());

        metadata = om.createObjectNode()
                .put(Field.Game.name(), qwds.trainingParams.gameName)
                .put(Field.Seed.name(), qwds.playerParams.getRandomSeed())
                .put(Field.Type.name(), qwds.playerParams.getType().name())
                .put(Field.Alpha.name(), qwds.trainingParams.alpha)
                .put(Field.Gamma.name(), qwds.trainingParams.gamma)
                .put(Field.Epsilon.name(), qwds.playerParams.epsilon)
                .put(Field.Solver.name(), qwds.trainingParams.solver.name())
                .put(Field.Heuristic.name(), qwds.trainingParams.heuristic.getClass().getCanonicalName())
                .put(Field.FeatureVector.name(), qwds.playerParams.getFeatureVectorCanonicalName())
                .put(Field.NGamesTotal.name(), nGamesPlayedFromInfile)
                .put(Field.NGamesWithTheseSettings.name(), 0);
        // TODO make separate function
        if (existingMetadata != null) {
            // Only keep existing metadata values that are different to current setup
            for (String fn : Arrays.stream(Field.getUniqueFields()).map(f -> f.name()).toList())
                if (existingMetadata.has(fn) && existingMetadata.get(fn).equals(metadata.get(fn)))
                    existingMetadata.remove(fn);
            // Check if only non-unique field names remain
            boolean allSettingsIdentical = true;
            Iterator<String> remainingFieldNames = existingMetadata.fieldNames();
            while (remainingFieldNames.hasNext()) {
                String fieldName = remainingFieldNames.next();
                if (!Arrays.asList(Field.getNonUniqueFields()).stream().map(c -> c.name()).toList().contains(fieldName))
                    allSettingsIdentical = false;
            }
            if (allSettingsIdentical) {
                // Merge settings
                String fn = Field.NGamesWithTheseSettings.name();
                metadata.put(fn, metadata.get(fn).asInt() + existingMetadata.get(fn).asInt());
                JsonNode _existing = existingMetadata.get(Field.ContinuedFrom.name());
                existingMetadata = _existing.isNull() ? null : (ObjectNode) _existing;
            }
            // Add existing to ContinuedFrom
        }
        metadata.set(Field.ContinuedFrom.name(), existingMetadata);
    }

    ObjectNode readFileMetadata(String pathname) {
        if (pathname == null)
            return null;
        try {
            JsonNode data = new ObjectMapper().readTree(new File(pathname));
            return (ObjectNode) data.get("Metadata");
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return null;
    }

    void initNextSegmentFile(int totalNGames) {
        updateSegmentOutfile(totalNGames);
    }

    void updateAndWriteFile(int nGamesToAdd) {
        addGames(nGamesToAdd);
        writeQWeightsToFile();
    }

    private void createMissingFoldersAndFiles() {
        DataProcessor.getGameFolderPath(qwds.trainingParams.gameName).toFile().mkdirs();
    }

    private void addGames(int nGamesToAdd) {
        for (Field f : Arrays.asList(Field.NGamesTotal, Field.NGamesWithTheseSettings))
            metadata.put(f.name(), metadata.get(f.name()).asInt() + nGamesToAdd);
    }

    private JsonNode createJsonNode(ObjectMapper objectMapper) {
        Map<String, Double> stateMap = qwds.qWeightsToStateMap();
        JsonNode weightsData = objectMapper.convertValue(stateMap, ObjectNode.class);
        ObjectNode outNode = objectMapper.createObjectNode();
        outNode.set("Metadata", metadata);
        outNode.set("Weights", weightsData);
        return outNode;
    }

    private void writeQWeightsToFile() {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode node = createJsonNode(objectMapper);

        outfile.setWritable(true);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outfile))) {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            writer.write(json);
            outfile.setReadOnly();
        } catch (IOException e) {
            System.out.println("An error occurred while writing beta to the file: " +
                    e.getMessage());
            System.exit(1);
        }
    }

    // region Reading input file
    public static JsonNode readInputFile(String gameName, String infileNameOrPath) {
        Path gameFolderPath = getGameFolderPath(gameName);
        Path[] pathsInOrderOfCheck = {
                Paths.get(gameFolderPath.toString(), infileNameOrPath), // Just filename
                Paths.get(RLPlayer.resourcesPathName, infileNameOrPath), // Path relative to resources folder
                Paths.get(infileNameOrPath), // Absolute path or relative to root folder
        };
        Path infilePath = Arrays.stream(pathsInOrderOfCheck).filter(p -> p.toFile().exists()).findFirst().orElse(null);
        if (infilePath == null)
            throw new IllegalArgumentException(
                    "No matching file found for input " + infileNameOrPath
                            + "Please provide a legal pathname, either as"
                            + "\n - An absolute path"
                            + "\n - A relative path to " + Paths.get("").toAbsolutePath()
                            + "\n - A relative path to " + Paths.get(RLPlayer.resourcesPathName).toAbsolutePath()
                            + "\n - A relative path to " + gameFolderPath.toAbsolutePath());
        try {
            File file = new File(infilePath.toString());
            return new ObjectMapper().readTree(file);
        } catch (IOException | IllegalArgumentException e) {
            e.printStackTrace();
            System.exit(1);
            return null;
        }
    }

    static Path getGameFolderPath(String gameName) {
        return Paths.get(RLPlayer.resourcesPathName, QWeightsDataStructure.qWeightsFolderName, gameName);
    }
    // endregion
}
