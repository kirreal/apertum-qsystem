/*
 *  Copyright (C) 2010 Apertum project. web: www.apertum.ru email: info@apertum.ru
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ru.apertum.qsystem.reports.formirovators;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import ru.apertum.qsystem.common.Uses;

/**
 * @author Igor Savin
 */
public class DistributionWaitDayServices extends AFormirovator {

    /**
     * Метод формирования параметров для отчета.
     * В отчет нужно передать некие параметры. Они упаковываются в Мар.
     * Если параметры не нужны, то сформировать пустой Мар.
     * @return
     */
    @Override
    public Map getParameters(String driverClassName, String url, String username, String password, String inputData) {
        return paramMap;
    }
    /**
     * Для параметров
     */
    final private HashMap<String, Object> paramMap = new HashMap<String, Object>();

    /**
     * Метод получения коннекта к базе если отчет строится через коннект.
     * Если отчет строится не через коннект, а формироватором, то выдать null.
     * @return коннект соединения к базе или null.
     */
    @Override
    public Connection getConnection(String driverClassName, String url, String username, String password, String inputData) {
        final Connection connection;
        try {
            Class.forName(driverClassName);
            connection = DriverManager.getConnection(url + (url.indexOf("?") == -1 ? "" : "&") + "user=" + username + "&password=" + password);
        } catch (SQLException ex) {
            throw new Uses.ReportException(StatisticServices.class.getName() + " " + ex);
        } catch (ClassNotFoundException ex) {
            throw new Uses.ReportException(StatisticServices.class.getName() + " " + ex);
        }
        return connection;
    }

    @Override
    public byte[] preparation(String driverClassName, String url, String username, String password, String inputData) {
        // если в запросе не содержаться введенные параметры, то выдыем форму ввода
        // иначе выдаем null.
        final String data = Uses.getRequestData(inputData);
        final String tmp = Uses.getRequestTarget(inputData);
        Uses.log.logger.trace("Принятые параметры \"" + data + "\".");
        Uses.log.logger.trace("subject \"" + tmp + "\".");
        // флаг введенности параметров
        boolean flag = false;
        String mess = "";
        if ("".equals(data)) {
            flag = true;
        } else {
            // проверка на корректность введенных параметров
            final String[] ss = data.split("&");
            if (ss.length == 3) {
                final String[] ss0 = ss[0].split("=");
                final String[] ss1 = ss[1].split("=");
                final String[] ss2 = ss[2].split("=");
                Date date = null;
                String sdate = null;
                int service_id = -1;
                String service = null;
                try {
                    date = Uses.format_dd_MM_yyyy.parse(ss0[1]);
                    sdate = (new java.text.SimpleDateFormat("yyyy-MM-dd")).format(date);
                    service_id = Integer.parseInt(ss1[1]);
                    service = ss2[1];
                } catch (Exception ex) {
                    mess = "<br>Ошибка ввода параметров! Не все параметры введены корректно (дд.мм.гггг).";
                    flag = true;
                }
                if (!flag) {
                    paramMap.put("sdate", sdate);
                    paramMap.put(ss0[0], date);
                    paramMap.put(ss1[0], new Integer(service_id));
                    paramMap.put(ss2[0], service);
                }
            } else {
                mess = "<br>Ошибка ввода параметров!";
                flag = true;
            }
        }
        if (flag) {
            // вставим необходимую ссылку на отчет в форму ввода
            // и выдадим ее клиенту на заполнение.
            // после заполнения вызовется нужный отчет с введенными параметрами и этот метод вернет null,
            // что продолжет генерить отчет методом getDataSource с нужными параметрами.
            // А здесь мы просто знаем какой формироватор должен какие формы выдавать пользователю. На то он и формироватор, индивидуальный для каждого отчета.
            final InputStream inStream = getClass().getResourceAsStream("/ru/apertum/qsystem/reports/web/get_date_distribution_services.html");
            String result = null;
            String services_select = "";
            try {
                result = new String(Uses.readInputStream(inStream), "UTF-8");
            } catch (IOException ex) {
                throw new Uses.ReportException("Ошибка чтения ресурса для диалога ввода сервиса. " + ex);
            }
            try {
                Connection conn = getConnection(driverClassName, url, username, password, inputData);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT id, name FROM services WHERE id NOT IN (SELECT DISTINCT prent_id FROM services WHERE prent_ID IS NOT NULL) ORDER BY name");
                int id;
                String svcname;
                services_select = "";
                while (rs.next()) {
                    id = rs.getInt(1);
                    svcname = rs.getString(2);
                    services_select += "<option value=" + id + ">" + svcname + "\n";
                }
            } catch (SQLException ex) {
                throw new Uses.ReportException("Ошибка выполнения запроса для диалога ввода сервиса. " + ex);
            }
            final String subject = Uses.getRequestTarget(inputData);
            result = result.replaceFirst(Uses.ANCHOR_DATA_FOR_REPORT, subject).replaceFirst(Uses.ANCHOR_ERROR_INPUT_DATA, mess).replaceFirst("#DATA_FOR_TITLE#", "Распределение среднего времени ожидания внутри дня для услуги:").replaceFirst("#DATA_FOR_SERVICES#", services_select);
            try {
                return result.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                return result.getBytes();
            }
        } else {
            return null;
        }
    }
}
