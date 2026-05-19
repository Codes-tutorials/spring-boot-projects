package org.codeart.validation.dto;

import lombok.Builder;
import lombok.Data;
import org.codeart.validation.groups.Create;
import org.codeart.validation.groups.Update;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.Range;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;

@Builder
@Data
public class UserDTO {
    @Null(groups = Create.class)
    @NotNull(groups = Update.class)
    @Range(max = Integer.MAX_VALUE)
    private Integer id;

    @Range(max = 200)
    private Integer age;

    @Length(max = 256)
    private String username;

    @Length(max = 256)
    private String address;

    @Valid
    private JobInfo jobInfo;
}
