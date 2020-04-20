package qldb;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IntegrationRequest {
    String action;
    List<Query> payload = new ArrayList<Query>();
}
