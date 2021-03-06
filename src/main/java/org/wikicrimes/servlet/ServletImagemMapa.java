/**
 
    WikiCrimes (http://www.wikicrimes.org) is a project/software that allows posting and accessing criminal occurrences in a digital map.
    The philosophy that drives Wikicrimes is the same as Wikipedia: mass collaboration produces valuable knowledge.
    That is to say, if everybody participates, the criminal mapping will be made collaboratively and everybody
    will leverage crime information digitalized in the map. That is the reason for the slogan "Share crime information. Keep safe!". 
    Wikicrimes is not a project developed by any security institution. 
    In fact it is a project from the citizen to the citizen. 
     
    
    Copyright (C) 2008  Wikinova Solutions (http://www.wikinova.com.br)

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
 **/
package org.wikicrimes.servlet;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.wikicrimes.model.BaseObject;
import org.wikicrimes.model.Crime;
import org.wikicrimes.model.ImagemMapa;
import org.wikicrimes.model.PontoLatLng;
import org.wikicrimes.model.Usuario;
import org.wikicrimes.service.CrimeService;
import org.wikicrimes.service.ImagemMapaService;
import org.wikicrimes.util.ServletUtil;
import org.wikicrimes.web.FiltroForm;
import org.wikicrimes.web.ImagemMapaForm;


/**
 * Trata requisi��es para gerar imagens do mapa. Fala com a Google Static Maps API.
 * 
 * @author victor
 */
@SuppressWarnings("serial")
public class ServletImagemMapa extends HttpServlet {

	private ImagemMapaService service;
	private CrimeService crimeService;
	private final int MARGEM = 10; //margem do pol�gono em rela��o � borda da imagem
	
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		
		ImagemMapaService service = getService();
		HttpSession sessao = req.getSession();
		String acao = req.getParameter("acao");
		String id = req.getParameter("id");
		
		if(acao != null){
			
			if(acao.equals("pegaImagem")){
				
				//recupera os dados no banco (objeto ImagemMapa)
				ImagemMapa im = service.get(Integer.valueOf(id));
				BufferedImage imagem = new BufferedImage(im.getWidth(), im.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
				
				//pega a imgaem (so o mapa) pela Google Static Maps API
				URL urlImagem = constroiUrlGSM(im);
				BufferedImage imagemMapa = ServletUtil.requestImage(urlImagem);
				imagem.getGraphics().drawImage(imagemMapa, 0, 0, null);
				
				//pinta o poligono por cima
				pintaPoligono(im, imagem);
				
				//pinta os marcadores por cima
				pintaMarcadores(im, imagem, sessao);
				
				ServletUtil.sendImage(resp, imagem);
			}
		}
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		
		HttpSession sessao = req.getSession();
		String acao = req.getParameter("acao");
		
//		/*teste*/System.out.println("params: " + req.getParameterMap().keySet());
		
		if(acao.equals("novaImagem")){
			
			//dados pra identificar a imagem
			String centroLat = req.getParameter("centroLat");
			String centroLng = req.getParameter("centroLng");
			String zoom = req.getParameter("zoom");
			String width = req.getParameter("width");
			String height = req.getParameter("height");
			String poligono = req.getParameter("poligono");
			String north = req.getParameter("north");
			String south = req.getParameter("south");
			String east = req.getParameter("east");
			String west = req.getParameter("west");
			
			//persistencia
			long id = constroiImagemMapa(centroLat, centroLng, zoom, width, height, poligono, north, south, east, west, sessao);
			
			//envia o URL q da acesso a imagem dentro dum html com outras informacoes
			PrintWriter out = resp.getWriter();
			String urlImagemMapa =  req.getScheme() + "://" + req.getServerName() + ":" 
									+ req.getServerPort() + req.getContextPath() + "/img.html?id=" + id; 
			out.write(urlImagemMapa);
//			/*teste*/System.out.println("urlImagemMapa: " + urlImagemMapa);
		}
	}
	
	private long constroiImagemMapa(String centroLat, String centroLng, String zoom, String width, String height, 
			String poligono, String north, String south, String east, String west, HttpSession sessao){
		ImagemMapaService service = getService();
		ImagemMapa im = new ImagemMapa();
		
		//centro, zoom, size
		PontoLatLng ponto = new PontoLatLng();
		ponto.setLatitude(Double.valueOf(centroLat));
		ponto.setLongitude(Double.valueOf(centroLng));
		service.save(ponto);
		im.setCentro(ponto);
		im.setZoom(Integer.valueOf(zoom));
		im.setWidth(Integer.valueOf(width));
		im.setHeight(Integer.valueOf(height));
		
		//poligono
		List<PontoLatLng> vertices = im.setPoligonoAndReturn(poligono);
		service.save(vertices);
		
		//bounds
		im.setNorth(Double.valueOf(north));
		im.setSouth(Double.valueOf(south));
		im.setEast(Double.valueOf(east));
		im.setWest(Double.valueOf(west));
		
		//data-hora
		im.setDataHoraRegistro(new Date());
		
		//usuario
		Usuario usuario = (Usuario)sessao.getAttribute("usuario");
		im.setUsuario(usuario);
		
		//filtro crimes
		FiltroForm filtro = (FiltroForm)sessao.getAttribute("filtroForm");
		im.setFiltro(filtro.getFiltroStringMap());
//		/*teste*/ System.out.println("filtro setado: " + im.getFiltro());
		
		service.save(im);
		return im.getIdImagemMapa();
	}
	
	private URL constroiUrlGSM(ImagemMapa im) throws MalformedURLException{
		//GSM = Google Static Maps
		PontoLatLng ponto = im.getCentro();
		String urlMapaLimpo = "http://maps.google.com/maps/api/staticmap?center=" + ponto.getLatitude() +","+ ponto.getLongitude() 
			+ "&zoom=" + im.getZoom() + "&size=" + (im.getWidth()+MARGEM*2) + "x" + (im.getHeight()+MARGEM*2) + "&sensor=false";
//			+ "&markers=" + latlngs;
		return new URL(urlMapaLimpo);
	}
	
	private void pintaPoligono(ImagemMapa im, Image imagem){
		int nPoints = im.getPoligono().size();
		int[] xPoints = new int[nPoints];
		int[] yPoints = new int[nPoints];
		for(int i=0; i<nPoints; i++){
			PontoLatLng latlng = im.getPoligono().get(i);
			Point pixel = toPixel(latlng, im);
			xPoints[i] = pixel.x;
			yPoints[i] = pixel.y;
		}
		Graphics2D g = (Graphics2D)imagem.getGraphics();
		g.setColor(new Color(0,0,1,.25f));
		g.fillPolygon(xPoints, yPoints, nPoints);
		g.setColor(new Color(0,0,1,.5f));
		g.setStroke(new BasicStroke(5));
		g.drawPolygon(xPoints, yPoints, nPoints);
	}
	
	private void pintaMarcadores(ImagemMapa im, Image imagem, HttpSession sessao){
		try{
	//		/*teste*/ System.out.println("filtro: " + im.getFiltro());
			Map<String, Object> param = ImagemMapaForm.getParams(im);
			List<BaseObject> crimes = getCrimeService().filter(param);
			Graphics2D g = (Graphics2D)imagem.getGraphics();
			ServletContext ctx = sessao.getServletContext();
			
			for(BaseObject o : crimes){
				if(o instanceof Crime){
					Crime c = (Crime)o;
					String tipo = c.getTipoCrime().getNome();
					String filePath = null;
					if(tipo.equals("tipocrime.roubo") || tipo.equals("tipocrime.tentativaderoubo"))
//						marcadorFile = new File("webapps/wikicrimes/images/baloes/vermelho.png");
						filePath = ctx.getRealPath("images/baloes/vermelho.png");
					else if(tipo.equals("tipocrime.furto") || tipo.equals("tipocrime.tentativadefurto"))
						filePath = ctx.getRealPath("images/baloes/novoMarcadorAzul.png");
					else
						filePath = ctx.getRealPath("/images/baloes/novoMarcadorLaranja.png");
					File marcadorFile = new File(filePath);
					Image marcador = ImageIO.read(marcadorFile);
					int height = marcador.getHeight(null);
					PontoLatLng latlng = new PontoLatLng(c.getLatitude(), c.getLongitude());
					Point p = toPixel(latlng, im);
					g.drawImage(marcador, p.x, p.y - height, null);
				}
			}
		}catch(IOException e){
			e.printStackTrace();
		}
	}
	
	private Point toPixel(PontoLatLng latlng, ImagemMapa im){
		int pixelWidth = im.getWidth();
		int pixelHeight = im.getHeight();
		double latlngWidth = im.getEast() - im.getWest();
		double latlngHeight = im.getSouth() - im.getNorth();
		int razaoWidth = (int)(pixelWidth/latlngWidth);
		int razaoHeight = (int)(pixelHeight/latlngHeight);
		int x = (int)((latlng.getLongitude()-im.getWest()) * razaoWidth + MARGEM);
		int y = (int)((latlng.getLatitude()-im.getNorth()) * razaoHeight + MARGEM);
		return new Point(x,y);
	}
	
	private ImagemMapaService getService(){
		if(service == null){
			ApplicationContext springContext = WebApplicationContextUtils.getWebApplicationContext(getServletContext());
			service = (ImagemMapaService)springContext.getBean("imagemMapaService");
		}
		return service;
	}
	
	private CrimeService getCrimeService(){
		if(crimeService == null){
			ApplicationContext springContext = WebApplicationContextUtils.getWebApplicationContext(getServletContext());
			crimeService = (CrimeService)springContext.getBean("crimeService");
		}
		return crimeService;
	}
	
}
	