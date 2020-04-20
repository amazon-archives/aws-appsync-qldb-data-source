package qldb;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IntegrationResponse {
    private String result;
    private boolean success = false;
    private String error;
}
