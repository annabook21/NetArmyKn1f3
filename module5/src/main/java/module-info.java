module edu.au.cpsc.module5 {
    requires javafx.controls;
    requires javafx.fxml;


    opens edu.au.cpsc.module5 to javafx.fxml;
    exports edu.au.cpsc.module5;
    opens edu.au.cpsc.miscstyle to javafx.fxml;
    exports edu.au.cpsc.miscstyle;
}