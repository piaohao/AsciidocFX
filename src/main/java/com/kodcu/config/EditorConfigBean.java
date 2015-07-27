package com.kodcu.config;

//import com.dooapp.fxform.annotation.Accessor;

import com.dooapp.fxform.FXForm;
import com.dooapp.fxform.builder.FXFormBuilder;
import com.dooapp.fxform.handler.NamedFieldHandler;
import com.dooapp.fxform.view.factory.DefaultFactoryProvider;
import com.kodcu.component.SliderBuilt;
import com.kodcu.config.factory.FileChooserFactory;
import com.kodcu.config.factory.ListChoiceBoxFactory;
import com.kodcu.config.factory.SliderFactory;
import com.kodcu.config.factory.SpinnerFactory;
import com.kodcu.controller.ApplicationController;
import com.kodcu.other.IOHelper;
import com.kodcu.service.ThreadService;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.joox.JOOX;
import org.joox.Match;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by usta on 17.07.2015.
 */
@Component
public class EditorConfigBean extends ConfigurationBase {

    private ObjectProperty<ObservableList<String>> editorTheme = new SimpleObjectProperty<>(FXCollections.observableArrayList());
    private BooleanProperty directoryPanel = new SimpleBooleanProperty(true);
    private StringProperty fontFamily = new SimpleStringProperty("'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', 'source-code-pro', monospace");
    private ObjectProperty<Integer> fontSize = new SimpleObjectProperty(14);
    private DoubleProperty scrollSpeed = new SimpleDoubleProperty(0.1);
    private ObjectProperty<ObservableList<String>> defaultLanguage = new SimpleObjectProperty<>(FXCollections.observableArrayList());
    private ObjectProperty<Path> kindlegen = new SimpleObjectProperty<>();


    private Logger logger = LoggerFactory.getLogger(EditorConfigBean.class);

    private final ApplicationController controller;
    private final ThreadService threadService;

    private final Button saveButton = new Button("Save");
    private final Button loadButton = new Button("Load");
    private final Label infoLabel = new Label();

    @Autowired
    public EditorConfigBean(ApplicationController controller, ThreadService threadService) {
        super(controller, threadService);
        this.controller = controller;
        this.threadService = threadService;
    }

    public Path getKindlegen() {
        return kindlegen.get();
    }

    public ObjectProperty<Path> kindlegenProperty() {
        return kindlegen;
    }

    public void setKindlegen(Path kindlegen) {
        this.kindlegen.set(kindlegen);
    }

    public ObservableList<String> getEditorTheme() {
        return editorTheme.get();
    }

    public ObjectProperty<ObservableList<String>> editorThemeProperty() {
        return editorTheme;
    }

    public void setEditorTheme(ObservableList<String> editorTheme) {
        this.editorTheme.set(editorTheme);
    }

    public boolean getDirectoryPanel() {
        return directoryPanel.get();
    }

    public BooleanProperty directoryPanelProperty() {
        return directoryPanel;
    }

    public void setDirectoryPanel(boolean directoryPanel) {
        this.directoryPanel.set(directoryPanel);
    }

    public String getFontFamily() {
        return fontFamily.get();
    }

    public StringProperty fontFamilyProperty() {
        return fontFamily;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily.set(fontFamily);
    }

    public Integer getFontSize() {
        return fontSize.get();
    }

    public ObjectProperty<Integer> fontSizeProperty() {
        return fontSize;
    }

    public void setFontSize(Integer fontSize) {
        this.fontSize.set(fontSize);
    }

    public double getScrollSpeed() {
        return scrollSpeed.get();
    }

    public DoubleProperty scrollSpeedProperty() {
        return scrollSpeed;
    }

    public void setScrollSpeed(double scrollSpeed) {
        this.scrollSpeed.set(scrollSpeed);
    }

    public ObservableList<String> getDefaultLanguage() {
        return defaultLanguage.get();
    }

    public ObjectProperty<ObservableList<String>> defaultLanguageProperty() {
        return defaultLanguage;
    }

    public void setDefaultLanguage(ObservableList<String> defaultLanguage) {
        this.defaultLanguage.set(defaultLanguage);
    }

    @Override
    public VBox createForm() {

        FXForm editorConfigForm = new FXFormBuilder<>()
                .resourceBundle(ResourceBundle.getBundle("editorConfig"))
                .includeAndReorder("editorTheme", "directoryPanel", "fontFamily", "fontSize", "scrollSpeed", "defaultLanguage", "kindlegen")
                .build();

        DefaultFactoryProvider editorConfigFormProvider = new DefaultFactoryProvider();

        editorConfigFormProvider.addFactory(new NamedFieldHandler("editorTheme"), new ListChoiceBoxFactory(new ChoiceBox()));
        editorConfigFormProvider.addFactory(new NamedFieldHandler("defaultLanguage"), new ListChoiceBoxFactory(new ChoiceBox()));
        editorConfigFormProvider.addFactory(new NamedFieldHandler("scrollSpeed"), new SliderFactory(SliderBuilt.create(0.0, 1, 0.1).step(0.1)));
        editorConfigFormProvider.addFactory(new NamedFieldHandler("fontSize"), new SpinnerFactory(new Spinner(8, 32, 14)));
        editorConfigFormProvider.addFactory(new NamedFieldHandler("kindlegen"), new FileChooserFactory());
        editorConfigForm.setEditorFactoryProvider(editorConfigFormProvider);

        editorConfigForm.setSource(this);

        VBox vBox = new VBox();
        vBox.getChildren().add(editorConfigForm);


        saveButton.setOnAction(this::save);
        loadButton.setOnAction(this::load);
        vBox.getChildren().add(new HBox(10, saveButton, loadButton, infoLabel));

        return vBox;
    }

    @Override
    public Path getConfigPath() {
        return getConfigDirectory().resolve("editor_config.json");
    }

    @Override
    public void load(ActionEvent... actionEvent) {

        fadeOut(infoLabel, "Loading...");

        threadService.runTaskLater(() -> {
            List<String> aceThemeList = IOHelper.readAllLines(getConfigDirectory().resolve("ace_themes.txt"));
            List<String> languageList = this.languageList();

            FileReader fileReader = IOHelper.fileReader(getConfigPath());
            JsonReader jsonReader = Json.createReader(fileReader);

            JsonObject jsonObject = jsonReader.readObject();

            boolean directoryPanel = jsonObject.getBoolean("directoryPanel", true);
            String fontFamily = jsonObject.getString("fontFamily", "'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', 'source-code-pro', monospace");
            int fontSize = jsonObject.getInt("fontSize", 14);
            String theme = jsonObject.getString("editorTheme", "xcode");
            String defaultLanguage = jsonObject.getString("defaultLanguage", "en");
            String kindlegen = jsonObject.getString("kindlegen", null);

            IOHelper.close(jsonReader, fileReader);

            threadService.runActionLater(() -> {
                this.setEditorTheme(FXCollections.observableList(aceThemeList));
                this.setDefaultLanguage(FXCollections.observableList(languageList));
                this.setDirectoryPanel(directoryPanel);
                this.setFontFamily(fontFamily);
                this.setFontSize(fontSize);

                if (Objects.nonNull(kindlegen)) {
                    this.setKindlegen(Paths.get(kindlegen));
                }


                if (jsonObject.containsKey("scrollSpeed")) {
                    this.setScrollSpeed(jsonObject.getJsonNumber("scrollSpeed").doubleValue());
                } else {
                    this.setScrollSpeed(0.1);
                }

                this.getEditorTheme().set(0, theme);
                this.getDefaultLanguage().set(0, defaultLanguage);

                fadeOut(infoLabel, "Loaded...");

            });
        });


    }

    private List<String> languageList() {

        Path configPath = controller.getConfigPath();

        Stream<Path> languageStream = IOHelper.list(configPath.resolve("docbook/common"));
        List<String> languageList = languageStream.parallel().filter(p -> !p.endsWith("xml"))
                .map(path -> {
                    try {
                        Match $ = JOOX.$(path.toFile());
                        String language = $.attr("language");
                        String languageName = $.attr("english-language-name");

                        Objects.requireNonNull(languageName);
                        Objects.requireNonNull(language);

                        return language;
                    } catch (Exception ex) {
                        // no-op
                    }

                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return languageList;
    }

    @Override
    public void save(ActionEvent... actionEvent) {

        infoLabel.setText("Saving...");
        saveJson(getJSON());
        fadeOut(infoLabel, "Saved...");
    }

    @Override
    public JsonObject getJSON() {
        JsonObjectBuilder objectBuilder = Json.createObjectBuilder();

        objectBuilder
                .add("directoryPanel", getDirectoryPanel())
                .add("fontFamily", getFontFamily())
                .add("fontSize", getFontSize())
                .add("scrollSpeed", getScrollSpeed())
                .add("editorTheme", getEditorTheme().get(0))
                .add("defaultLanguage", getDefaultLanguage().get(0));

        if (Objects.nonNull(getKindlegen())) {
            objectBuilder.add("kindlegen", getKindlegen().toString());
        }

        return objectBuilder.build();
    }
}
