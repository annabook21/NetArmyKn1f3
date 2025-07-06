module edu.au.cpsc.module7 {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.logging;


    opens edu.au.cpsc.module7 to javafx.fxml;
    exports edu.au.cpsc.module7;
}