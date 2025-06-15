module edu.au.cpsc.module4 {
    requires javafx.controls;
    requires javafx.fxml;


    opens edu.au.cpsc.module4 to javafx.fxml;
    exports edu.au.cpsc.module4;
}