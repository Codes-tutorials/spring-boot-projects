package org.codeart.session.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/session")
public class SessionController {

    @GetMapping("/info")
    public Map<String, Object> getSessionInfo(HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", session.getId());
        response.put("creationTime", session.getCreationTime());
        response.put("lastAccessedTime", session.getLastAccessedTime());

        Integer hits = (Integer) session.getAttribute("hits");
        if (hits == null) {
            hits = 0;
        }
        session.setAttribute("hits", ++hits);
        response.put("hits", hits);

        return response;
    }

    @GetMapping("/set")
    public String setAttribute(HttpSession session, @RequestParam String key, @RequestParam String value) {
        session.setAttribute(key, value);
        return "Attribute set: " + key + " = " + value;
    }

    @GetMapping("/get")
    public String getAttribute(HttpSession session, @RequestParam String key) {
        return "Attribute: " + key + " = " + session.getAttribute(key);
    }
}
