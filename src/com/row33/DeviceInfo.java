package com.row33;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;

public class DeviceInfo {
	private static final Logger log = Logger.getLogger("com.row33.netonoff");
	
	private long id;
	private String mac;
	private String name;
	private boolean enabled4 = true;
	private boolean enabled6 = true;
	
	public static class InvalidDeviceInfo extends Exception {
		private static final long serialVersionUID = -4239536877689469278L;
		public InvalidDeviceInfo(String s) {
			super(s);
		}
	};
	
	public static DeviceInfo create(String mac, String name, Connection c) throws SQLException, InvalidDeviceInfo {
		log.info("create");
		if (c == null || c.isClosed()) {
			return null;
		}
		
		mac = mac.replaceAll(":",  "");
		if (mac.length() != 12) {
			throw new InvalidDeviceInfo("MAC is incorrect length");
		}
		mac = mac.toUpperCase();
		mac = mac.replaceAll("[^0-9A-F]", "");
		if (mac.length() != 12) {
			throw new InvalidDeviceInfo("non-hex chars in MAC");
		}
		
		if (name.length() > 250) {
			name = name.substring(0, 250);
		}
		
		Long gen_id = null;
		try (
			PreparedStatement st = c.prepareStatement("INSERT INTO devices(mac,name) VALUES(?,?)");
		) {
			st.setString(1, mac);
			st.setString(2, name);
			st.executeUpdate();
			
			try (
				ResultSet rs = st.getGeneratedKeys()
			) {
				if (rs.next()) {
					gen_id = rs.getLong(1);
				}
			}
			catch (SQLException ex) {
				throw ex;
			}
		}
		catch (SQLException ex) {
			throw ex;
		}
		
		if (gen_id != null) {
			return load(gen_id, c);
		}
		else {
			return null;
		}
	}
	
	public static DeviceInfo load(Long id, Connection c) throws SQLException {
		if (c == null || c.isClosed()) {
			return null;
		}
		
		try (
			Statement st = c.createStatement();
			ResultSet rs = st.executeQuery(String.format("SELECT id, mac, name FROM devices WHERE id = %d", id));
		) {
			if (rs.next()) {
				return new DeviceInfo(rs.getLong(1), rs.getString(2), rs.getString(3));
			}
		}
		catch (SQLException ex) {
			throw ex;
		}
		
		return null;
	}
	
	public static List<DeviceInfo> fetchAll(Connection c) throws SQLException {
		ArrayList<DeviceInfo> dl = null;
		
		if (c == null || c.isClosed()) {
			return null;
		}
		
		// get tracked device list from DB
		try (
			Statement st = c.createStatement();
			ResultSet rs = st.executeQuery("SELECT id, mac, name FROM devices")
		) {
			dl = new ArrayList<DeviceInfo>();	
			while(rs.next()) {
				dl.add(new DeviceInfo(rs.getLong(1), rs.getString(2), rs.getString(3)));
			}
		}
		catch (SQLException ex) {
			throw ex;
		}
		
		// get current firewall state for each device
		List<String> tbl4 = getTable(true);
		List<String> tbl6 = getTable(false);
		
		for (DeviceInfo d : dl) {
			if (tbl4.contains(d.getMac())) {
				d.enabled4 = false;
			}
			if (tbl6.contains(d.getMac())) {
				d.enabled6 = false;
			}
		}
		
		return dl;
	}
	
	private static List<String> getTable(boolean ipv4) {
		ArrayList<String> tbl = null;
		
		StringWriter writer = new StringWriter();
		String tables = "/sbin/iptables";
		if (!ipv4) {
			tables = "/sbin/ip6tables";
		}
		ProcessBuilder pb = new ProcessBuilder("/usr/bin/sudo", tables, "-L", "FW_FILTER_KOUT");
		pb.redirectErrorStream(true);
		
		try {
			Process p = pb.start();
			p.waitFor();
			
			IOUtils.copy(p.getInputStream(), writer, "utf8");
			String[] lines = writer.toString().split("\n");
			
			tbl = new ArrayList<String>();
			
			for (int i = 0; i < lines.length; i++) {
				String line = lines[i];
				if (line.startsWith("REJECT_OUT")) {
					String mac = line.substring(line.length() - 17);
					tbl.add(mac);
				}
			}
		}
		catch (Exception ex) {
			return null;
		}
		
		return tbl;
	}
	
	public static void enableAll() {
		ProcessBuilder pb4 = new ProcessBuilder("/usr/bin/sudo", "/sbin/iptables", "-F", "FW_FILTER_KOUT");
		ProcessBuilder pb6 = new ProcessBuilder("/usr/bin/sudo", "/sbin/ip6tables", "-F", "FW_FILTER_KOUT");
		pb4.redirectErrorStream(true);
		pb6.redirectErrorStream(true);
		try {
			Process p4 = pb4.start();
			Process p6 = pb6.start();
			p4.waitFor();
			p6.waitFor();
		}
		catch (Exception ex) {
			log.warning(String.format("failed to flush iptables: %s", ex));
		}
	}
	
	private DeviceInfo(long i, String m, String n) {
		id = i;
		mac = m;
		name = n;
	}
	
	public long getId() {
		return id;
	}
	
	public String getMac() {
		String fmac = null;
		for (int i = 0; i < 12; i = i + 2) {
			String b = mac.substring(i, i + 2);
			if (i == 0) {
				fmac = b;
			}
			else {
				fmac = fmac + ":" + b;
			}
		}
		return fmac;
	}
	
	public String getName() {
		return name;
	}
	
	public boolean isEnabled() {
		return enabled4 && enabled6;
	}
	
	public void disable() {
		log.info(String.format("disable %s %s", getName(), getMac()));
		disableIfNeeded(getId(), getMac(), true);
		disableIfNeeded(getId(), getMac(), false);
	}
	
	private static void disableIfNeeded(long id, String mac, boolean ipv4) {
		String tables = "/sbin/iptables";
		if (!ipv4) {
			tables = "/sbin/ip6tables";
		}
		
		ProcessBuilder pbC = new ProcessBuilder("/usr/bin/sudo", tables, "-C", "FW_FILTER_KOUT",
			"-m", "mac", "--mac-source", mac, "-j", "REJECT_OUT");
		ProcessBuilder pbA = new ProcessBuilder("/usr/bin/sudo", tables, "-A", "FW_FILTER_KOUT",
			"-m", "mac", "--mac-source", mac, "-j", "REJECT_OUT");
		
		boolean needrule = false;
		try {
			Process pC = pbC.start();
			pC.waitFor();
			int eval = pC.exitValue();
			if (eval == 1) {
				needrule = true;
			}
		}
		catch (IOException | InterruptedException ex) {
			log.warning(String.format("failed to check rule status: %s", ex));
			return;
		}
		
		if (!needrule) {
			return;
		}
		
		try {
			Process pA = pbA.start();
			pA.waitFor();
		}
		catch (IOException | InterruptedException ex) {
			log.warning(String.format("failed to add rule: %s", ex));
			return;
		}
		
		return;
	}
	
	public void enable() {
		log.info(String.format("enable %s %s", getName(), getMac()));
		enableIfNeeded(getId(), getMac(), true);
		enableIfNeeded(getId(), getMac(), false);
	}
	
	private static void enableIfNeeded(long id, String mac, boolean ipv4) {
		String tables = "/sbin/iptables";
		if (!ipv4) {
			tables = "/sbin/ip6tables";
		}
		
		ProcessBuilder pbC = new ProcessBuilder("/usr/bin/sudo", tables, "-C", "FW_FILTER_KOUT",
			"-m", "mac", "--mac-source", mac, "-j", "REJECT_OUT");
		ProcessBuilder pbA = new ProcessBuilder("/usr/bin/sudo", tables, "-D", "FW_FILTER_KOUT",
			"-m", "mac", "--mac-source", mac, "-j", "REJECT_OUT");
		
		boolean needrule = false;
		try {
			Process pC = pbC.start();
			pC.waitFor();
			int eval = pC.exitValue();
			log.info(String.format("eval: %d", eval));
			if (eval == 0) {
				needrule = true;
			}
		}
		catch (IOException | InterruptedException ex) {
			log.warning(String.format("failed to check rule status: %s", ex));
			return;
		}
		
		if (!needrule) {
			return;
		}
		
		try {
			Process pA = pbA.start();
			pA.waitFor();
		}
		catch (IOException | InterruptedException ex) {
			log.warning(String.format("failed to remove rule: %s", ex));
			return;
		}
		
		return;
	}
	
}
