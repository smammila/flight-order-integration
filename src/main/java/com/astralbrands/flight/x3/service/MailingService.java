package com.astralbrands.flight.x3.service;

import java.util.Properties;

import javax.activation.DataHandler;
import javax.annotation.PostConstruct;
import javax.mail.BodyPart;
import javax.mail.Multipart;
import javax.mail.Message.RecipientType;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import com.astralbrands.flight.x3.util.AppConstants;

@Component
public class MailingService implements AppConstants {
	Logger log = LoggerFactory.getLogger(MailingService.class);

	@Value("${smtp.host}")
	private String host;
	@Value("${smtp.port}")
	private String port;
	@Value("${smtp.username}")
	private String userName;
	@Value("${smtp.password}")
	private String password;
	@Value("${smtp.from}")
	private String from;
	@Value("${smtp.to}")
	private String toList;
	JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();

	@PostConstruct
	public void init() {
		javaMailSender.setHost(host);
		javaMailSender.setPort(587);

		javaMailSender.setUsername(userName);
		javaMailSender.setPassword(password);

		Properties props = javaMailSender.getJavaMailProperties();
		props.put("mail.transport.protocol", "smtp");
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.starttls.required", "true");
		props.put("mail.smtpClient.EnableSsl", "false");
		props.put("mail.debug", "true");
	}

	public void sendMailWithAttachment(String fileData, String subject, String fileName) {

		log.info("sending .........");

		MimeMessage message = javaMailSender.createMimeMessage();
		try {

			if (toList != null && toList.length() > 0 && toList.contains(SEMI_COMMA)) {
				String[] toAdd = toList.split(SEMI_COMMA);
				for (String to : toAdd) {
					message.addRecipient(RecipientType.TO, new InternetAddress(to));
				}
			} else {
				message.addRecipient(RecipientType.TO, new InternetAddress(toList));
			}

			message.setFrom(from);
			message.setSubject(subject);
			BodyPart msgBody = new MimeBodyPart();
			msgBody.setText("Hi Team, \n Please find Attached order forms. \n Thanks,\n Shiva");

			Multipart multiPart = new MimeMultipart();
			MimeBodyPart attachFilePart = new MimeBodyPart();
			attachFilePart.setDataHandler(new DataHandler(
					new ByteArrayDataSource(fileData.replaceAll(IFILE_DATA_SEPERATOR, COMMA).getBytes(), "text/csv")));
			attachFilePart.setFileName(fileName + ".csv");

			MimeBodyPart iFileBody = new MimeBodyPart();
			iFileBody.setDataHandler(new DataHandler(new ByteArrayDataSource(fileData.getBytes(), "text/plain")));
			iFileBody.setFileName(fileName + ".txt");
			multiPart.addBodyPart(iFileBody);
			multiPart.addBodyPart(attachFilePart);
			multiPart.addBodyPart(msgBody);
			message.setContent(multiPart);

		} catch (Exception e) {
			e.printStackTrace();
		}
		//javaMailSender.send(message);
		System.out.println("email send with subject : " + subject);

	}

	public void sendMail(String body, String subject) {

		SimpleMailMessage msg = new SimpleMailMessage();
		msg.setFrom(from);
		msg.setSubject(subject);
		msg.setText(body);
		msg.setTo(toList.split(";"));
		javaMailSender.send(msg);
		System.out.println("email send with subject : " + subject);

	}

}
