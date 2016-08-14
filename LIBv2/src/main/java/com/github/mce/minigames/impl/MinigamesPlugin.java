/*
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.github.mce.minigames.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.comze_instancelabs.minigamesapi.MinigamesAPI;
import com.github.mce.minigames.api.CommonErrors;
import com.github.mce.minigames.api.LibState;
import com.github.mce.minigames.api.MglibInterface;
import com.github.mce.minigames.api.MinecraftVersionsType;
import com.github.mce.minigames.api.MinigameException;
import com.github.mce.minigames.api.MinigameInterface;
import com.github.mce.minigames.api.MinigamePluginInterface;
import com.github.mce.minigames.api.PluginProviderInterface;
import com.github.mce.minigames.api.arena.ArenaInterface;
import com.github.mce.minigames.api.arena.ArenaTypeInterface;
import com.github.mce.minigames.api.cmd.CommandHandlerInterface;
import com.github.mce.minigames.api.cmd.CommandInterface;
import com.github.mce.minigames.api.config.ConfigurationValueInterface;
import com.github.mce.minigames.api.locale.LocalizedMessageInterface;
import com.github.mce.minigames.api.perms.PermissionsInterface;
import com.github.mce.minigames.api.player.ArenaPlayerInterface;
import com.github.mce.minigames.api.zones.ZoneInterface;
import com.github.mce.minigames.impl.cmd.PartyCommandHandler;
import com.github.mce.minigames.impl.cmd.StartCommandHandler;

/**
 * A plugin for minigames.
 * 
 * @author mepeisen
 *
 */
public class MinigamesPlugin extends JavaPlugin implements MglibInterface
{
    
    /** the well known minigames. */
    private final Map<String, MinigamePluginImpl> minigames = new ConcurrentHashMap<>();
    
    /** Current library state. */
    private LibState state = LibState.Initializing;
    
    /** known command handlers by name. */
    private final Map<String, CommandHandlerInterface> commands = new HashMap<>();
    
    /**
     * Constructor to create the plugin.
     */
    public MinigamesPlugin()
    {
        // registers the core minigame.
        try
        {
            this.register(new PluginProviderInterface() {
                
                @Override
                public String getName()
                {
                    return MglibInterface.CORE_MINIGAME;
                }
                
                @Override
                public Iterable<Class<? extends Enum<?>>> getMessageClasses()
                {
                    final List<Class<? extends Enum<?>>> result = new ArrayList<>();
                    result.add(CommonErrors.class);
                    return result;
                }
                
                @Override
                public JavaPlugin getJavaPlugin()
                {
                    return MinigamesPlugin.this;
                }

                @Override
                public Map<String, CommandHandlerInterface> getBukkitCommands()
                {
                    final Map<String, CommandHandlerInterface> result = new HashMap<>();
                    result.put("start", new StartCommandHandler()); //$NON-NLS-1$
                    result.put("party", new PartyCommandHandler()); //$NON-NLS-1$
                    // TODO
                    return result;
                }

                @Override
                public Iterable<Class<? extends Enum<?>>> getPermissions()
                {
                    // TODO Auto-generated method stub
                    return null;
                }

                @Override
                public Iterable<Class<? extends Enum<?>>> getConfigurations()
                {
                    // TODO Auto-generated method stub
                    return null;
                }
            }).init();
        }
        catch (MinigameException ex)
        {
            // log it, although this should never happen
            // because in constructor we neither are in wrong state
            // nor do we already know the 'core' minigame
            ex.printStackTrace();
        }
    }
    
    // event handlers
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        final CommandHandlerInterface handler = this.commands.get(command.getName().toLowerCase());
        if (handler != null)
        {
            try
            {
                final CommandInterface cmd = null; // TODO
                handler.handle(cmd);
            }
            catch (MinigameException ex)
            {
                // TODO Logging
                final Locale locale = Locale.ENGLISH; // TODO
                final boolean isAdmin = true; // TODO
                final String msg = isAdmin ? (ex.getCode().toAdminMessage(locale, ex.getArgs())) : (ex.getCode().toUserMessage(locale, ex.getArgs()));
                switch (ex.getCode().getSeverity())
                {
                    default:
                    case Error:
                        sender.sendMessage(ChatColor.DARK_RED + msg);
                        break;
                    case Information:
                        sender.sendMessage(ChatColor.WHITE + msg);
                        break;
                    case Loser:
                        sender.sendMessage(ChatColor.RED + msg);
                        break;
                    case Success:
                        sender.sendMessage(ChatColor.GREEN + msg);
                        break;
                    case Warning:
                        sender.sendMessage(ChatColor.YELLOW + msg);
                        break;
                    case Winner:
                        sender.sendMessage(ChatColor.GOLD + msg);
                        break;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args)
    {
        // TODO Auto-generated method stub
        return super.onTabComplete(sender, command, alias, args);
    }
    
    // api methods

    @Override
    public LibState getState()
    {
        return this.state;
    }

    @Override
    public MinecraftVersionsType getMinecraftVersion()
    {
        return MinecraftVersionsType.valueOf(MinigamesAPI.SERVER_VERSION.name());
    }
    
    @Override
    public MinigamePluginInterface register(PluginProviderInterface provider) throws MinigameException
    {
        final String name = provider.getName();
        
        MinigamePluginImpl impl;
        
        synchronized (this.minigames)
        {
            if (this.state != LibState.Initializing || this.state != LibState.Sleeping)
            {
                throw new MinigameException(CommonErrors.Cannot_Create_Game_Wrong_State, name, this.state.name());
            }
            if (this.minigames.containsKey(name))
            {
                throw new MinigameException(CommonErrors.DuplicateMinigame, name);
            }
            
            impl = new MinigamePluginImpl(name, provider);
            this.minigames.put(name, impl);
        }
        
        return impl;
    }

    @Override
    public MinigameInterface getMinigame(String minigame)
    {
        final MinigamePluginImpl impl = this.minigames.get(minigame);
        return impl == null ? null : new MinigameWrapper(impl);
    }

    /* (non-Javadoc)
     * @see com.github.mce.minigames.api.MglibInterface#findZone(org.bukkit.Location)
     */
    @Override
    public ZoneInterface findZone(Location location)
    {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.github.mce.minigames.api.MglibInterface#findZones(org.bukkit.Location)
     */
    @Override
    public Iterable<ZoneInterface> findZones(Location location)
    {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.github.mce.minigames.api.MglibInterface#getPlayer(org.bukkit.entity.Player)
     */
    @Override
    public ArenaPlayerInterface getPlayer(Player player)
    {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.github.mce.minigames.api.MglibInterface#getPlayer(org.bukkit.OfflinePlayer)
     */
    @Override
    public ArenaPlayerInterface getPlayer(OfflinePlayer player)
    {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.github.mce.minigames.api.MglibInterface#getPlayer(java.util.UUID)
     */
    @Override
    public ArenaPlayerInterface getPlayer(UUID uuid)
    {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.github.mce.minigames.api.MglibInterface#getArenaTypes()
     */
    @Override
    public Iterable<ArenaTypeInterface> getArenaTypes()
    {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.github.mce.minigames.api.MglibInterface#getArenas()
     */
    @Override
    public Iterable<ArenaInterface> getArenas()
    {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.github.mce.minigames.api.MglibInterface#getArenas(com.github.mce.minigames.api.arena.ArenaTypeInterface)
     */
    @Override
    public Iterable<ArenaInterface> getArenas(ArenaTypeInterface type)
    {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.github.mce.minigames.api.MglibInterface#getMinigameFromMsg(com.github.mce.minigames.api.locale.LocalizedMessageInterface)
     */
    @Override
    public MinigameInterface getMinigameFromMsg(LocalizedMessageInterface item)
    {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.github.mce.minigames.api.MglibInterface#getMinigameFromPerm(com.github.mce.minigames.api.perms.PermissionsInterface)
     */
    @Override
    public MinigameInterface getMinigameFromPerm(PermissionsInterface item)
    {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.github.mce.minigames.api.MglibInterface#getMinigameFromCfg(com.github.mce.minigames.api.config.ConfigurationValueInterface)
     */
    @Override
    public MinigameInterface getMinigameFromCfg(ConfigurationValueInterface item)
    {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.github.mce.minigames.api.MglibInterface#getContext(java.lang.Class)
     */
    @Override
    public <T> T getContext(Class<T> clazz)
    {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.github.mce.minigames.api.MglibInterface#resolveContextVar(java.lang.String)
     */
    @Override
    public String resolveContextVar(String src)
    {
        // TODO Auto-generated method stub
        return null;
    }
    
    
    
}