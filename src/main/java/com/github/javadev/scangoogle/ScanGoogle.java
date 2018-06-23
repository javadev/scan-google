package com.github.javadev.scangoogle;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class ScanGoogle {

    private static final String SEARCH_WORDS_FILE_NAME = "words.csv";

    public static void main(String... args) throws Exception {
        final WebDriver driver = new ChromeDriver();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> driver.quit(), "Shutdown-thread"));

        List<String> searchStrings = readSearchStrings();
        searchStrings.forEach(searchString -> {
            System.out.println("Searching " + searchString);
            List<UrlData> datas = getSearchResultForQuery(driver, searchString);
            generateCsv(searchString + ".csv", datas);
            System.out.println(searchString + "\n" + readFile(searchString + ".csv"));
        });
        //Close the browser
        driver.quit();
    }

    private static List<UrlData> getSearchResultForQuery(WebDriver driver, String query) {
        try {
            driver.navigate().to("https://www.google.com/");
            WebElement element = driver.findElement(By.name("q"));
            element.sendKeys("\"" + query + "\" -inurl:.be -inurl:lu -inurl:ma -inurl:ch filetype:doc OR filetype:pdf OR filetype:docx");
            element.submit();

            List<UrlData> result = getPageResult(driver);

            while (result.size() < 20 && result.size() > 0) {
                driver.findElement(By.id("pnnext")).click();
                List<UrlData> localResult = getPageResult(driver);
                result.addAll(localResult);
                if (localResult.isEmpty()) {
                    break;
                }
            }
            return result;
        } catch (Exception ex) {
            Logger.getLogger(ScanGoogle.class.getName()).log(Level.SEVERE, null, ex);
        }
        return Collections.emptyList();
    }

    private static List<UrlData> getPageResult(WebDriver driver) throws Exception {
        Thread.sleep(30000 + new Random().nextInt(10000));
// wait until the google page shows the result
        (new WebDriverWait(driver, 10))
                .until(ExpectedConditions.presenceOfElementLocated(By.id("resultStats")));

        List<WebElement> findElements = driver.findElements(By.xpath("//*[@id='rso']//h3/a"));

        // this are all the links you like to visit
        return findElements.stream().map((webElement)
                -> getUrlInfo(webElement.getAttribute("href"))
        ).collect(Collectors.toList());
    }

    private static UrlData getUrlInfo(String url) {
        try {
            Response resp = Jsoup.connect(url).ignoreContentType(true)
                    .validateTLSCertificates(false).timeout(10 * 1000).execute();
            return new UrlData(url, resp.contentType(),
                    ZonedDateTime.now(ZoneId.systemDefault()), resp.bodyAsBytes().length);
        } catch (IOException ex) {
            Logger.getLogger(ScanGoogle.class.getName()).log(Level.SEVERE, null, ex);
        }
        return new UrlData(url, "", ZonedDateTime.now(ZoneId.systemDefault()), 0L);
    }

    private static class UrlData implements HeadersAndValues {

        public String url;
        public String type;
        public ZonedDateTime dateTime;
        public long size;

        private UrlData(String url, String type, ZonedDateTime dateTime, long size) {
            this.url = url;
            this.type = type;
            this.dateTime = dateTime;
            this.size = size;
        }

        @Override
        public String[] getHeaders() {
            return new String[]{"url", "type", "date", "size"};
        }

        @Override
        public List<String> getValues() {
            return Arrays.asList(url, type, dateTime.toString(), "" + size);
        }

        @Override
        public String toString() {
            return "UrlData{" + "url=" + url + ", type=" + type + ", dateTime=" + dateTime + ", size=" + size + '}';
        }
    }

    private static List<String> readSearchStrings() {
        try {
            List<String> result = new ArrayList<>();
            try (Reader reader = new FileReader(SEARCH_WORDS_FILE_NAME)) {
                Iterable<CSVRecord> records = CSVFormat.RFC4180.withSkipHeaderRecord().withHeader("string").parse(reader);
                for (CSVRecord record : records) {
                    result.add(record.size() >= 1 ? record.get("string") : "");
                }
            }
            return result;
        } catch (IOException ex) {
        }
        return shuffle(Arrays.asList("Projet de contrat",
                "Accord de Partenariat",
                "Accord de Confidentialité",
                "Contrat-cadre",
                "Contrat d'édition",
                "Contrat de travail",
                "Contrat de licence",
                "Contrat de distribution",
                "Contrat de cession",
                "Contrat d’abonnement",
                "Contrat d’apporteur d’affaires",
                "Contrat d’agent commercial",
                "Contrat de prestations",
                "Contrat de vente",
                "Contrat de maintenance",
                "Contrat de conception-construction",
                "Contrat de franchise",
                "Contrat de location",
                "Contrat de bail",
                "Contrat d’ouvrage",
                "Traité de fusion",
                "Convention de séquestre"
        ));
    }

    private interface HeadersAndValues {

        String[] getHeaders();

        List<String> getValues();
    }

    private static void generateCsv(String fileName, List<? extends HeadersAndValues> data) {
        if (data.isEmpty()) {
            return;
        }
        try (
                BufferedWriter writer = Files.newBufferedWriter(Paths.get(fileName));
                CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                        .withHeader(data.get(0).getHeaders()));) {
            for (HeadersAndValues item : data) {
                csvPrinter.printRecord(item.getValues());
            }
            csvPrinter.flush();
        } catch (IOException ex) {
            Logger.getLogger(ScanGoogle.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static <E> List<E> shuffle(final List<E> iterable) {
        final List<E> shuffled = new ArrayList<>(iterable);
        Collections.shuffle(shuffled);
        return shuffled;
    }

    private static String readFile(String path) {
        try {
            byte[] encoded = Files.readAllBytes(Paths.get(path));
            return new String(encoded, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException ex) {
        }
        return "";
    }
}
