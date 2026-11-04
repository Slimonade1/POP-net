package dk.pop.status.temp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;

@Configuration
public class SecurityConfig {

    @Bean
    public OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        OidcUserService delegate = new OidcUserService();
        return userRequest -> {
            OidcUser user = delegate.loadUser(userRequest);
            var authorities = new ArrayList<GrantedAuthority>(user.getAuthorities());
            var groups = user.getClaimAsStringList("groups");
            // Only users explicitly placed in this IdP group receive application admin access.
            if (groups != null && groups.contains("popnet-admins")) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }
            return new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo(), "preferred_username");
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                        // Keep every admin page and its related actions restricted to the mapped admin role.
                        .requestMatchers("/admin", "/admin/**", "/messages/**",
                                "/retry-thresholds/**", "/email-notifications/**").hasRole("ADMIN")
                        .anyRequest().permitAll()
                )
                .oauth2Login(login -> login
                        .loginPage("/login")
                        .defaultSuccessUrl("/admin", true)
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService()))
                        .permitAll()
                )
                .logout(logout -> logout.permitAll());

        return http.build();
    }
}
