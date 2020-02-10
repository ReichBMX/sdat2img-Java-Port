package com.android.utility.ui.romimage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Credits to the original authors noted below
 * 
 * This is a port of their sdat2img.py script for decoding the system.new.dat file using the system.transfer.list offsets
 * 
 * @author bmxandxxx
 * @credits xpirt - luxi78 - howellzhu
 *
 */
public class sdat2img_port {

	private static final Logger logger = Logger.getLogger(sdat2img_port.class.getName());
	
	private static String DIR_NAME = "Scripts";

	private String TRANSFER_LIST_FILE;// = DIRECTORY + "system.transfer.list";
	private String NEW_DATA_FILE;// = DIRECTORY + "system.new.dat";
	private String OUTPUT_IMAGE_FILE;// = DIRECTORY + "system.img";

	private int VERSION = 0;

	private List<Command> commands = null;

	//TRANSFER_LIST_FILE, NEW_DATA_FILE, OUTPUT_IMAGE_FILE
	public sdat2img_port(String TRANSFER_LIST_FILE, String NEW_DATA_FILE, String OUTPUT_IMAGE_FILE) {

		this.TRANSFER_LIST_FILE = TRANSFER_LIST_FILE;
		this.NEW_DATA_FILE = NEW_DATA_FILE;
		this.OUTPUT_IMAGE_FILE = OUTPUT_IMAGE_FILE;

	}

	public static void main(String[] args) {

		String DIRECTORY = 
				System.getProperty("user.home") + File.separator + 
				DIR_NAME + 
				File.separator;//"/home/bmxandxxx/android_workplace/AospExtended-v6.6-herolte-20190704-1947-UNOFFICIAL/";
		sdat2img_port sdat = 
				new sdat2img_port(
					DIRECTORY + "system.transfer.list", 
					DIRECTORY + "system.new.dat", 
					DIRECTORY + "system.img"
				);
		sdat.execute();

	}

	public void execute() {

		parseListFile();
		versionCheck();
		applyBlockChanges();

	}


	private void versionCheck() {

		if (VERSION == 1)
			print("Android Lollipop 5.0 detected!\n");
		else if (VERSION == 2)
			print("Android Lollipop 5.1 detected!\n");
		else if (VERSION == 3)
			print("Android Marshmallow 6.x detected!\n");
		else if (VERSION == 4)
			print("Android Nougat 7.x / Oreo 8.x detected!\n");
		else
			print("Unknown Android version!\n");

	}

	private void applyBlockChanges() {

		int BLOCK_SIZE = 4096;
		long max_file_size = getMaxFileSize() * BLOCK_SIZE;

		File image_file = new File(OUTPUT_IMAGE_FILE);

		if (image_file.exists() && image_file.delete()) {
			print("System Image Exists... Deleting...");
		} else {
			print("System Image Exists... Could'nt Delete...");
		}

		try (RandomAccessFile new_data_file = new RandomAccessFile(new File(NEW_DATA_FILE), "rw");
				RandomAccessFile output_img = new RandomAccessFile(image_file, "rw")
				) {		
			byte[] buffer = new byte[BLOCK_SIZE];

			for (Command command : commands) {
				if (command.getCommand().equals("new")) {

					RangeSet set = command.getRangeSet();
					for (IntegerSet is : set.getSetList()) {

						long begin = is.getBeginning();
						long end = is.getEnd();
						long block_count = end - begin;

						print("Copying " + block_count + " blocks into position " + begin);

						long seek = begin * BLOCK_SIZE;

						output_img.seek(seek);

						while (block_count > 0) {
							new_data_file.read(buffer, 0, buffer.length);
							output_img.write(buffer, 0, buffer.length);
							block_count -= 1;
						}
					}
				} else {
					print("Skipping command " + command.getCommand() + "...");
				}
			}

			// Make file larger if necessary
			if(output_img.length() < max_file_size) {
				output_img.setLength(max_file_size);
			}
		} catch (Exception ex) {
			logger.log(Level.SEVERE, ex.getMessage());
		}

	}

	private long getMaxFileSize() {
		long max = 0;
		for (Command c : commands) { 
			for (IntegerSet is : c.getRangeSet().getSetList()) {
				long current = is.getEnd();
				if (current > max) {
					max = current;
				}
			}
		}
		return max;
	}

	private void parseListFile() {

		File trFile = new File(TRANSFER_LIST_FILE);
		try {
			FileReader fr = new FileReader(trFile);
			BufferedReader br = new BufferedReader(fr);

			commands = new ArrayList<>();

			String read = "";
			int lineNumber = 1;
			while ((read = br.readLine()) != null) {

				if (lineNumber == 1) {
					VERSION = Integer.parseInt(read);
				}
				String[] line = read.split(" ");
				String cmd = line[0];

				String[] cmds = {
						"erase", "new", "zero"
				};

				for (String c : cmds) {
					
					if (c.equals(cmd)) {
						String integers = line[1];
						String[] sep = integers.split(",");
						
						int totalBlocks = Integer.parseInt(sep[0]);
						
						RangeSet rs = new RangeSet();
						List<Integer> newList = new ArrayList<>();
						
						for (int set = 0; set < totalBlocks + 1; set++) {	
							if (set != 0) {
								newList.add(Integer.parseInt(sep[set]));
							}
						}
						
						List<Integer> ones = new ArrayList<>();
						List<Integer> twos = new ArrayList<>();
						
						for (int one = 0; one < totalBlocks; one += 2) {
							ones.add(
									newList.get(one)
									);
							twos.add(
									newList.get(one + 1)
									);
						}
						for (int set = 0; set < ones.size(); set++) {
							
							int a = ones.get(set);
							int b = twos.get(set);
							IntegerSet is = 
								new IntegerSet(
										a,
										b
								);
							rs.addSet(is);
						}

						commands.add(new Command(c, rs));
					}
				}

				lineNumber++;
			}
			print("Total Commands: " + commands.size());
			fr.close();
			br.close();
		} catch (FileNotFoundException e) {
			print("File Not Found: " + trFile.getAbsolutePath());
		} catch (IOException e) {
			print("Disk Read Error at Location: " + trFile.getAbsolutePath());
		}

	}

	private void print(String string) {
		logger.log(Level.INFO, string);
	}

	protected class RangeSet {

		private List<IntegerSet> setList = new ArrayList<>();

		protected RangeSet() {

		}

		public void addSet(IntegerSet set) {
			setList.add(set);
		}

		public List<IntegerSet> getSetList() {
			return setList;
		}

	}

	protected class IntegerSet {

		private long beginning;
		private long end;

		public IntegerSet(long beginning, long end) {
			this.beginning = beginning;
			this.end = end;
		}

		public long getBeginning() {
			return beginning;
		}

		public long getEnd() {
			return end;
		}

	}

	protected class Command {

		private String command;
		private RangeSet rangeSet;

		public Command(String command, RangeSet rangeSet) {
			this.setCommand(command);
			this.setRangeSet(rangeSet);
		}

		public String getCommand() {
			return command;
		}

		public void setCommand(String command) {
			this.command = command;
		}

		public RangeSet getRangeSet() {
			return rangeSet;
		}

		public void setRangeSet(RangeSet rangeSet) {
			this.rangeSet = rangeSet;
		}

	}

}
