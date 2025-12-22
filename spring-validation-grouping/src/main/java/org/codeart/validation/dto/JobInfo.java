package org.codeart.validation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JobInfo {
    @NotNull
    @Length(max = 64)
    private String company;

    @NotNull
    @Length(max = 64)
    private String position;
}
