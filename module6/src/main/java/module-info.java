module edu.au.cpsc.module6 {
    requires javafx.controls;
    requires javafx.fxml;


    opens edu.au.cpsc.module6 to javafx.fxml;
    exports edu.au.cpsc.module6;
}