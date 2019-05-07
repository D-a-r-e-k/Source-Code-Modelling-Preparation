package dariusb;

import dariusb.languages.JavaKeywords;
import japa.parser.JavaParser;
import japa.parser.TokenMgrError;
import japa.parser.ast.CompilationUnit;
import japa.parser.ast.body.*;

import javax.sound.midi.SysexMessage;
import java.io.*;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {

    private static final int MAXTOKENS = 1000;
    private static final int FILESLIMIT = 4000;

    private static HashMap<String, Integer> tokensPopularity = new HashMap<String, Integer>();
    private static LinkedList<ArrayList<String>> allTokens = new LinkedList<ArrayList<String>>();
    private static int allTokensCount = 0;
    private static int failedFilesCount = 0;

    public static void main(String[] args) {
        if (args.length != 4)
            throw new IllegalArgumentException();

        //transformSmellsDataset(args[2], args[3]);

        var files = new LinkedList<File>();
        getFiles(args[0], files);

        System.out.println(files.size());

        allTokensCount = 0;
        failedFilesCount = 0;
        tokensPopularity = new HashMap<String, Integer>();
        allTokens = new LinkedList<ArrayList<String>>();
        int i = 0;
        for (var file : files) {
            processFile(file.getAbsolutePath(), true);

            ++i;

            if (i % 100 == 0)
                System.out.println("Processed " + i + " files.");
        }

        System.out.println("Total tokens: " + allTokensCount);
        System.out.println("Total unique tokens: " + tokensPopularity.size());
        System.out.println("Total methods: " + allTokens.size());

        var dictionary = Utils.getKMostPopular(tokensPopularity, MAXTOKENS);

        try {
            try (var out = new FileWriter(Paths.get(args[1], "dictionary_large.csv").toString())) {
                for (var el : dictionary) {

                    out.write(el);
                    out.write(System.lineSeparator());
                }
            }

            try (var out = new FileWriter(Paths.get(args[1], "code_corpus_large.csv").toString())) {
                for (var methodTokens : allTokens) {
                    methodTokens.replaceAll(e -> !dictionary.contains(e) ? "<unk>" : e);

                    out.write(String.join(",",methodTokens));
                    out.write(System.lineSeparator());
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void transformSmellsDataset(String source, String target) {
        File directory = new File(source);

        File[] directories = directory.listFiles();
        for (File subDirectory : directories) {
            String ultimateTarget = Paths.get(target, subDirectory.getName()).toString();
            new File(ultimateTarget).mkdirs();

            allTokens = new LinkedList<ArrayList<String>>();
            var files = subDirectory.listFiles();
            for (File file : files) {
                processFile(file.getAbsolutePath(), false);
            }

            int i = 0;
            for (var methodTokens : allTokens) {
                try (var out = new FileWriter(Paths.get(ultimateTarget, files[i++].getName()).toString())) {
                    out.write(String.join(",", methodTokens));
                    out.write(System.lineSeparator());
                } catch (IOException e) {
                    System.out.println("Failed transforming file.");
                }
            }
        }
    }

    private static void processFile(String path, boolean trackPopularity) {

        CompilationUnit cu = null;
        try {
            String content = readAllText(path);

            if (!trackPopularity) {
                content = "public class Test { " + content + " }";
            }

            cu = JavaParser.parse(
                    new ByteArrayInputStream(content.getBytes("UTF-16")), "UTF-16");

            var methods = extractAllMethods(cu.getTypes());

            Tokenizer tokenizer = new Tokenizer(JavaKeywords.getInstance());
            for (var method : methods) {
                var tokens = tokenizer.tokenizeInput(method);
                tokens.replaceAll(e -> e.length() > 1 ? e.replaceAll("\\p{Punct}", "") : e);
                tokens.replaceAll(e -> e.equals(",") ? "<comma>" : e);
                tokens.replaceAll(e -> Utils.isNumeric(e) ? "<num>" : e);

                allTokensCount += tokens.size();
                allTokens.add(tokens);

                if (trackPopularity) {
                    for (var token : tokens) {
                        if (tokensPopularity.containsKey(token)) {
                            tokensPopularity.put(token, tokensPopularity.get(token) + 1);
                        } else {
                            tokensPopularity.put(token, 0);
                        }
                    }
                }
            }

        } catch (TokenMgrError err) {
            System.out.println("Parsing error.");
            failedFilesCount++;
        } catch (Exception e) {
            if (++failedFilesCount % 50 == 0)
                System.out.println("Failed files count: " + failedFilesCount);
        }
    }

    private static void getFiles(String directoryName, List<File> files) {
        if (files.size() > FILESLIMIT)
            return;

        File directory = new File(directoryName);

        File[] fList = directory.listFiles();
        if(fList != null)
            for (File file : fList) {
                if (file.isFile() && Math.random() < 0.05) {
                    files.add(file);
                } else if (file.isDirectory()) {
                    getFiles(file.getAbsolutePath(), files);
                }
            }
    }

    private static List<String> retrieveMethods(List<BodyDeclaration> members) {
        var methods = members.stream()
                .filter(x -> x instanceof MethodDeclaration)
                .map(x -> x.toString())
                .collect(Collectors.toList());

        return methods;
    }

    private static String constructArtificialHeaderMethod(List<BodyDeclaration> members) {
        var result = new StringBuilder();

        result.append("void method0() { " + System.lineSeparator());

        members.stream()
                .filter(x -> x instanceof FieldDeclaration)
                .forEach(x -> result.append(x.toString() + System.lineSeparator()));

        result.append("}");

        return result.toString();
    }

    private static List<String> extractAllMethods(List<TypeDeclaration> types) throws Exception {

        var allMethods = new LinkedList<String>();

        for (TypeDeclaration type : types) {
            var members = type.getMembers();
            var methods = retrieveMethods(members);

            allMethods.addAll(methods);
            //allMethods.add(constructArtificialHeaderMethod(members));
        }

        return allMethods;
    }

    private static String readAllText(String filePath) {
        BufferedReader br = null;
        var sb = new StringBuilder();

        try {

            br = new BufferedReader(new FileReader(filePath));
            int c = 0;
            while ((c = br.read()) != -1) {
                sb.append((char) c);
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return sb.toString();
    }
}
