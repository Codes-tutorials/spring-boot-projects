package org.codeart.validation.controller;

import org.codeart.validation.dto.JobInfo;
import org.codeart.validation.dto.R;
import org.codeart.validation.dto.UserDTO;
import org.codeart.validation.groups.Create;
import org.codeart.validation.groups.Update;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.Range;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/user")
public class UserController {

    @GetMapping("/{id}")
    public R<UserDTO> getById(@Range(max = Integer.MAX_VALUE) @PathVariable("id") Integer id) {
        UserDTO user = UserDTO.builder()
                .id(id)
                .age(200)
                .username("Obama")
                .address("LA")
                .jobInfo(new JobInfo("Government", "President"))
                .build();
        return R.ok(user);
    }

    @PostMapping("/create")
    public String create(@Validated(Create.class) @RequestBody UserDTO user) {
        System.out.println(user);
        return "creating user succeeded";
    }

    @PutMapping("/update")
    public String update(@Validated(Update.class) @RequestBody UserDTO user) {
        System.out.println(user);
        return "updating user succeeded";
    }

    @DeleteMapping("/del")
    public String delById(@Length(max = 64) @RequestParam("id") String id) {
        System.out.println(id);
        return "deleting user succeeded";
    }
}
