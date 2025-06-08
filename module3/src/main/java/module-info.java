module edu.au.cpsc.module3 {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;


    opens edu.au.cpsc.module3 to javafx.fxml;
    exports edu.au.cpsc.module3;
}