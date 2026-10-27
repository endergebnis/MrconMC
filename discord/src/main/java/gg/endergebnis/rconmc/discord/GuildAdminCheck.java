package gg.endergebnis.rconmc.discord;

import gg.endergebnis.rconmc.whitelist.GuildConfigService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import org.springframework.stereotype.Component;

/** Admin = "Server verwalten" oder die per {@code /setup} konfigurierte Admin-Rolle der jeweiligen Guild. */
@Component
public class GuildAdminCheck {

    private final GuildConfigService guilds;

    public GuildAdminCheck(GuildConfigService guilds) {
        this.guilds = guilds;
    }

    public boolean isAdmin(Member member) {
        if (member.hasPermission(Permission.MANAGE_SERVER)) {
            return true;
        }
        return guilds.adminRoleId(member.getGuild().getIdLong())
                .map(roleId -> member.getRoles().stream().anyMatch(role -> role.getIdLong() == roleId))
                .orElse(false);
    }
}
