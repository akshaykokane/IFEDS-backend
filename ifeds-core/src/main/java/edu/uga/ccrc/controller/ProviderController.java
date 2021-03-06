package edu.uga.ccrc.controller;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import edu.uga.ccrc.config.JwtTokenUtil;
import edu.uga.ccrc.dao.DataFileDAO;
import edu.uga.ccrc.dao.DatasetDAO;
import edu.uga.ccrc.dao.PermissionsDAO;
import edu.uga.ccrc.dao.ProviderDAO;
import edu.uga.ccrc.dao.SampleDAO;
import edu.uga.ccrc.entity.Dataset;
import edu.uga.ccrc.entity.Permissions;
import edu.uga.ccrc.entity.Provider;
import edu.uga.ccrc.exception.EntityNotFoundException;
import edu.uga.ccrc.exception.ForbiddenException;
import edu.uga.ccrc.exception.NoResposneException;
import edu.uga.ccrc.exception.SQLException;
import edu.uga.ccrc.service.JwtUserDetailsService;
import edu.uga.ccrc.view.bean.ChangePasswordBean;
import edu.uga.ccrc.view.bean.DashboardBean;
import edu.uga.ccrc.view.bean.DatasetBean;
import edu.uga.ccrc.view.bean.ProviderBean;
import edu.uga.ccrc.view.bean.ResetPasswordBean;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.ApiResponse;

@CrossOrigin
@RestController
public class ProviderController {
	
	@Autowired
	ProviderDAO providerDao;

	@Autowired
	DatasetDAO datasetDAO;
	
	@Autowired
	ResourceLoader resourceLoader;
	
	@Autowired
	DataFileDAO dataFileDAO;
	
	@Autowired
	SampleDAO sampleDAO;
	
	@Value("${jasypt.encryptor.password}")
	private String password;
	
	@Autowired
	private JwtTokenUtil jwtTokenUtil;
	
	@Autowired
    private JavaMailSender javaMailSender;

	
	@Value("${password_rest.link}")
	private  String password_reset_link;
	
	@Value("${password_rest.token_size}")
	private  int length_of_password_reset_token;
	
	@Value("${new_user.email.template_path}")
	private  String new_user_email_template_path;
		
	@Value("${password_reset.email.template_path}")
	private  String password_reset_email_template_path;
	
	@Autowired
	PermissionsDAO permissionsDAO;
	
	final static Logger log = LoggerFactory.getLogger(ProviderController.class);
	
	private boolean userIsAdmin(String username) {
		
		Provider provider = providerDao.findByUsername(username);
		
		Permissions p = permissionsDAO.findByProviderId(provider.getProviderId());
		log.info("Provider id : "+p.getPermission_level());
		if(p.getPermission_level().equals("admin"))
			return true;
		
		return false;
		
	}
	

	@RequestMapping(method = RequestMethod.POST, value = "/create_user", produces="application/json")
	@ApiOperation(value = "Create Provider(user)", response = ProviderBean.class)
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Success"),
			@ApiResponse(code = 403, message = "Accessing the Provider Info is forbidden"),
			@ApiResponse(code = 404, message = "Username, Email and Name is required"),
			@ApiResponse(code = 404, message = "{FiledName} characters greater than the allowed(64)"),
			@ApiResponse(code = 404, message = "Username already in use"),
			@ApiResponse(code = 404, message = "Email already in use"),
			@ApiResponse(code = 403, message = "You don't have access to this web service"),
			@ApiResponse(code = 500, message = "Internal Server Error")})
	public String createUser(HttpServletRequest request, @RequestBody ProviderBean providerBean) throws ForbiddenException, SQLException, NoResposneException, EntityNotFoundException {
		
		final String requestTokenHeader = request.getHeader("Authorization");
		
		String jwtToken = requestTokenHeader.substring(7);
		String username = jwtTokenUtil.getUsernameFromToken(jwtToken);
		
		if(!userIsAdmin(username))
			throw new ForbiddenException("You don't have access to this web service");
		
		Provider provider = new Provider();
		
		if(providerBean.getName() == null || providerBean.getName().length() < 4)
			throw new SQLException("Name is required field with minimum 4 characters");
		
		if(providerBean.getUsername() == null || providerBean.getUsername().length() < 4)
			throw new SQLException("Username is required field with minimum 4 characters");
		
		if(providerBean.getEmail() == null || providerBean.getEmail().length() < 5 )
			throw new SQLException("Email is required field with minimum 5 characters");
		
		if(providerBean.getEmail().length() > 64)
			throw new SQLException("Email characters greater than the allowed(64)");
		

		if(providerBean.getUsername().length() > 64)
			throw new SQLException("Username characters greater than the allowed(64)");
		
		if(providerBean.getName().length() > 64)
			throw new SQLException("Name characters greater than the allowed(64)");
		
		if(providerBean.getProviderGroup() != null && providerBean.getProviderGroup().length() > 64)
			throw new SQLException("Provider Group characters greater than the allowed(64)");
		
		if(providerBean.getDepartment() != null && providerBean.getDepartment().length() > 64)
			throw new SQLException("Provider Group characters greater than the allowed(64)");
		

		if(providerBean.getAffiliation() != null && providerBean.getAffiliation().length() > 64)
			throw new SQLException("Affiliation characters greater than the allowed(64)");
		
		
		if(providerBean.getUrl() != null && providerBean.getUrl().length() > 256)
			throw new SQLException("URL characters greater than the allowed(257)");
		
		if(providerBean.getContact() != null && providerBean.getContact().length() > 32)
			throw new SQLException("Contact characters greater than the allowed(257)");
		
		provider.setName(providerBean.getName());
		provider.setDepartment(providerBean.getDepartment());
		provider.setProviderGroup(providerBean.getProviderGroup());
		provider.setAffiliation(providerBean.getAffiliation());
		provider.setUrl(providerBean.getUrl());
		provider.setContact(providerBean.getContact());
		provider.setEmail(providerBean.getEmail());
		provider.setUsername(providerBean.getUsername());
		provider.setActive(true);
		
		if(providerDao.findByEmail(provider.getEmail()) != null)
			throw new SQLException("Email already in use");
		
		if(providerDao.findByUsername(provider.getUsername()) != null)
			throw new SQLException("Username already in use");
		
		String token = generateToken();
		
		String link = sendEmail(token, provider.getEmail(), provider.getName(), true);
		
		provider.setPassword("RESET "+token);
		provider.setReset_token("RESET "+token);
		
		
		try {
			providerDao.save(provider);
		}catch(Exception e){
			throw new NoResposneException("Something went wrong");
		}
		return "{\n\t message : New user created. Please check email for password link}";
		
	}
	

	
	
	@RequestMapping(method = RequestMethod.GET, value = "/getProvider", produces="application/json")
	@ApiOperation(value = "Get Provider Info", response = ProviderBean.class)
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Success"),
			@ApiResponse(code = 403, message = "Accessing the Provider Info is forbidden"),
			@ApiResponse(code = 404, message = "The Provider Info is not found") })
	public ProviderBean findInformation(HttpServletRequest request, HttpServletResponse response) {
		log.info("Retrieving provider information : findByUsername() ");
		final String requestTokenHeader = request.getHeader("Authorization");
		
		String jwtToken = requestTokenHeader.substring(7);
		String username = jwtTokenUtil.getUsernameFromToken(jwtToken);
		Provider provider = providerDao.findByUsername(username); 
		
		ProviderBean providerBean = new ProviderBean();
		providerBean.setName(provider.getName());
		providerBean.setContact(provider.getContact());
		providerBean.setAffiliation(provider.getAffiliation());
		providerBean.setProviderGroup(provider.getProviderGroup());
		providerBean.setDepartment(provider.getDepartment());
		providerBean.setEmail(provider.getEmail());
		providerBean.setProviderId(provider.getProviderId());
		providerBean.setUrl(provider.getUrl());
		
		return providerBean;
		
		
	}
	
	@RequestMapping(method = RequestMethod.PUT, value = "/update_provider", produces="application/json")
	@ApiOperation(value = "Updates Provider(User profile) Info", response = ProviderBean.class)
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Success"),
			@ApiResponse(code = 400, message = "SQL Exception"),
			@ApiResponse(code = 403, message = "Accessing the Provider Info is forbidden"),
			@ApiResponse(code = 404, message = "The Provider Info is not found") })
	public String updateProviderInformation(HttpServletRequest request, HttpServletResponse response, @RequestBody ProviderBean providerBean) throws EntityNotFoundException, SQLException, NoResposneException {
		
		log.info("Updating provider information ");
		final String requestTokenHeader = request.getHeader("Authorization");
		String jwtToken = requestTokenHeader.substring(7);
		String username = jwtTokenUtil.getUsernameFromToken(jwtToken);
		
		if(username == null)
			throw new EntityNotFoundException("Invalid username");
		
		Provider provider = providerDao.findByUsername(username); 
		
		
		if(providerBean.getName().length() > 64)
			throw new SQLException("Name should be less than 64 character");
		provider.setName(providerBean.getName());
		
		if(providerBean.getAffiliation() != null && providerBean.getAffiliation().length() > 64)
			throw new SQLException("Affilation should be less than 64 character");
		provider.setAffiliation(providerBean.getAffiliation());

		if(providerBean.getContact() != null && providerBean.getContact().length() > 32)
			throw new SQLException("Contact should be less than 32 character");
		provider.setContact(providerBean.getContact());
		
		if(providerBean.getDepartment() != null && providerBean.getDepartment().length() > 64)
			throw new SQLException("Department should be less than 64 character");
		provider.setDepartment(providerBean.getDepartment());

		if(providerBean.getUrl() != null && providerBean.getUrl().length() > 256)
			throw new SQLException("URL should be less than 256 character");
		provider.setUrl(providerBean.getUrl());
		
		if(providerBean.getProviderGroup() != null && providerBean.getProviderGroup().length() > 64)
			throw new SQLException("Groups should be less than 64 character");
		provider.setProviderGroup(providerBean.getProviderGroup());

		try {
			provider = providerDao.save(provider);
			return "{\n\t message : Successfully updated Provider's information \n}";
		}
		catch(Exception e) {
			throw new NoResposneException("Something went wrong");
		}
		
		
		
	}

	@RequestMapping(method = RequestMethod.GET, value = "/getProviderDatasets", produces="application/json")
	@ApiOperation(value = "Get Provider Dataset", response = DatasetBean.class)
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Success"),
			@ApiResponse(code = 403, message = "Accessing provider dataset is forbidden"),
			@ApiResponse(code = 404, message = "The provider dataset is not found") })
	public List<DatasetBean> getProviderDataSets(HttpServletRequest request, HttpServletResponse response) throws EntityNotFoundException {
		log.info("Retrieving provider's uploaded dataset information : findByUsername() ");		
		
		final String requestTokenHeader = request.getHeader("Authorization");
		String jwtToken = requestTokenHeader.substring(7);
		String username = jwtTokenUtil.getUsernameFromToken(jwtToken);

		Provider provider = providerDao.findByUsername(username); 
		String providerName = provider.getName();
		List<DatasetBean> result =new ArrayList<DatasetBean>();
	
		for (Dataset ds : datasetDAO.findAll()) {
			Provider currentProvider = ds.getProvider();
			if(currentProvider.getName().equals(providerName) ) {
				DatasetBean b=new DatasetBean();
				b.setDatasetId(ds.getDatasetId());
				b.setDatasetName(ds.getName());
				b.setDescription(ds.getDescription());
				b.setSampleName(ds.getSample().getName());
				b.setProviderName(ds.getProvider().getName());
				int number_of_files = dataFileDAO.numberOfDataFiles(ds.getDatasetId());
				b.setNum_of_files(number_of_files);
				result.add(b);	
			}
			
		}
		return result;
	
	}
	
	@RequestMapping(method = RequestMethod.GET, value = "/dashboard", produces="application/json")
	@ApiOperation(value = "Dashboard WS", response = DashboardBean.class)
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Success"),
			@ApiResponse(code = 403, message = "Accessing provider dataset is forbidden"),
			@ApiResponse(code = 404, message = "The provider dataset is not found") })
	public DashboardBean getDashboardDetails(HttpServletRequest request, HttpServletResponse response) throws EntityNotFoundException {
		

		final String requestTokenHeader = request.getHeader("Authorization");
		String jwtToken = requestTokenHeader.substring(7);
		String username = jwtTokenUtil.getUsernameFromToken(jwtToken);

		Provider provider = providerDao.findByUsername(username); 
		DashboardBean db = new DashboardBean();
		
		int num_of_dataset = datasetDAO.findByProvider(provider.getProviderId());
		int num_of_samples = sampleDAO.findSampleByProviderId(provider.getProviderId()).size();
		
		db.setNum_of_dataset(num_of_dataset);
		db.setNum_of_samples(num_of_samples);
		
		return db;
		
	}
	@RequestMapping(method = RequestMethod.POST, value = "/change_password", produces="application/json")
	@ApiOperation(value = "Change password", response = ChangePasswordBean.class)
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Success"),
			@ApiResponse(code = 400, message = "SQL Exception"),
			@ApiResponse(code = 403, message = "Bad URL Request"),
			@ApiResponse(code = 404, message = "Requested URL not found") })
	public String Change_Password(HttpServletRequest request, HttpServletResponse response, @RequestBody ChangePasswordBean changePassword) throws EntityNotFoundException, SQLException, NoResposneException {
		final String requestTokenHeader = request.getHeader("Authorization");
		String jwtToken = requestTokenHeader.substring(7);
		String username = jwtTokenUtil.getUsernameFromToken(jwtToken);
		BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
		Provider provider = providerDao.findByUsername(username);
		boolean userAuthenticate = passwordEncoder.matches(changePassword.getOld_password(), provider.getPassword());
		if(userAuthenticate){	
			try {
				if(changePassword.getNew_password().length() > 64) 
					throw new SQLException("Name should be less than 64 character");
				provider.setPassword(passwordEncoder.encode(changePassword.getNew_password()));
				providerDao.save(provider);
				return "{\n\t Success \n}";
			}
			catch(Exception e ){
				log.error("Old password doesn't match with user password");
			}
			
		}
		return "{\n\t Success \n}";

	}	
	
	
	@RequestMapping(method = RequestMethod.GET, value = "/reset_password/{username_or_email}", produces="application/json")
	@ApiOperation(value = "Reset password. Generate token for resetting the password", response = String.class)
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Success"),
			@ApiResponse(code = 400, message = "SQL Exception"),
			@ApiResponse(code = 403, message = "Bad URL Request"),
			@ApiResponse(code = 404, message = "Requested URL not found") })
	public String PasswordResetToken(@PathVariable String username_or_email) throws EntityNotFoundException, SQLException, NoResposneException {
		Provider provider = providerDao.findByUsername(username_or_email);
		
		if(provider == null)
			provider = providerDao.findByEmail(username_or_email);
		
		if(provider == null)
			throw new SQLException("Invalid username or email");
		
		String token = generateToken();
		sendEmail(token, provider.getEmail(), provider.getName(), false);
		provider.setReset_token("RESET "+token);
		
		try {
			providerDao.save(provider);
		}catch(Exception e){
			throw new NoResposneException("Cannot reset the password. Please try after sometime");
		}
		return "{\n\t message : success \n}";
		
	}
	
	private String generateToken() 
    { 
        // chose a Character random from this String 
        String AlphaNumericString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                                    + "0123456789"
                                    + "abcdefghijklmnopqrstuvxyz"; 
  
        // create StringBuffer size of AlphaNumericString 
        StringBuilder sb = new StringBuilder(length_of_password_reset_token); 
  
        for (int i = 0; i < length_of_password_reset_token; i++) { 
  
            // generate a random number between 
            // 0 to AlphaNumericString variable length 
            int index 
                = (int)(AlphaNumericString.length() 
                        * Math.random()); 
  
            // add Character one by one in end of sb 
            sb.append(AlphaNumericString 
                          .charAt(index)); 
        } 
  
        return sb.toString(); 
    } 
	private String sendEmail(String token, String email, String name, boolean isNewUser) throws NoResposneException, EntityNotFoundException {
		
		log.info("New user created. Sending password link to email to email " + email);
		
		SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(email);

        msg.setSubject("IFEDs Password Reset Link");
        msg.setText(generateEmailBody(name, password_reset_link+"" + token, isNewUser));
       
        try {
        	javaMailSender.send(msg);	
        	return password_reset_link+"" + token;
        }catch(Exception e)
        {
        	log.error("Error occured while sending email"+ e.getMessage());
        	throw new EntityNotFoundException("Send email not working");
        }
        
	}
	
	private String generateEmailBody(String name, String link, boolean newUser) throws NoResposneException {
		try {
			Resource resource = null;
			if(newUser)
				resource = resourceLoader.getResource(new_user_email_template_path);
			else
				resource = resourceLoader.getResource(password_reset_email_template_path);
			
		    InputStream inputStream = resource.getInputStream();
		    byte[] bdata = FileCopyUtils.copyToByteArray(inputStream);
	        String data = new String(bdata, StandardCharsets.UTF_8);
		    Scanner myReader = new Scanner(data);
		    StringBuilder emailBody = new StringBuilder();
		      while (myReader.hasNextLine()) {
		        String line = myReader.nextLine();
		        if(line.contains("[")) {
		        	int startIndex = line.indexOf('[');
		        	int endIndex = line.lastIndexOf(']');
		        	
		        	switch(line.substring(startIndex, endIndex + 1)) {
		        		case "[NAME]" : 
		        			line = line.substring(0, startIndex) + name+""+line.substring(endIndex + 1); 
		        			break;
		        		case "[LINK]":
		        			line = line.substring(0, startIndex) +link+ line.substring(endIndex + 1); 
		        			break;
		        	}
		        }
		        emailBody.append(line+"\n");
		        
		      }
		      myReader.close();
		      return emailBody.toString();
		    } catch (FileNotFoundException e) {
		    	return "Hello "+name+" \n Your password reset link is "+link;
		      //throw new NoResposneException("Email Template Not Found");
		    
		    }catch (Exception e) {
		    	 throw new NoResposneException("Something went wrong while sending email!");
		    }
	}
	
	@RequestMapping(method = RequestMethod.POST, value = "/password_reset/{token}", produces="application/json")
	@ApiOperation(value = "Reset Password", response = ResetPasswordBean.class)
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Success"),
			@ApiResponse(code = 400, message = "SQL Exception"),
			@ApiResponse(code = 403, message = "Bad URL Request"),
			@ApiResponse(code = 404, message = "Requested URL not found") })
	public String resetPassword(HttpServletRequest request, HttpServletResponse response, @RequestBody ResetPasswordBean resetPassword, @PathVariable String token) throws EntityNotFoundException, SQLException, NoResposneException, ForbiddenException{
		BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
		
		String user = resetPassword.getUsername();
		String resetToken = token;
		Provider provider = providerDao.findByUsername(user);
		String reset_token = provider.getReset_token();
		
		if(reset_token.equals(resetToken)) {

			
			if(resetPassword.getNew_password().length() > 64) 
				throw new SQLException("Name should be less than 64 character");
				
			provider.setPassword(passwordEncoder.encode(resetPassword.getNew_password()));
			
			try {
				providerDao.save(provider);
				return "{\n\t message : success \n}";
			}
			catch(Exception e ){
				throw new  ForbiddenException("Failed to save new password. Please try after sometime");
			}
		
		}
		
		throw new ForbiddenException("Token does not match");
		
		
	
	}
  
}
