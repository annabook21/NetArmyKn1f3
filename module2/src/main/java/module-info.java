module edu.au.cpsc.module2 {
    requires javafx.controls;
    requires javafx.fxml;


    opens edu.au.cpsc.module2 to javafx.fxml;
    exports edu.au.cpsc.module2;
}