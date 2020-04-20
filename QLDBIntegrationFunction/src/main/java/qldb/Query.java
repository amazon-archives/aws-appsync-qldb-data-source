package qldb;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Query {
    String query;
    List<String> args = new ArrayList<String>();

    public boolean hasJmesPathArgument() {
        return args.stream().anyMatch(a -> a.startsWith("$."));
    }
}
