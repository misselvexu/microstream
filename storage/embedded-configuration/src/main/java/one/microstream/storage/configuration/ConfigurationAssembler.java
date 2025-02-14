package one.microstream.storage.configuration;

/*-
 * #%L
 * microstream-storage-embedded-configuration
 * %%
 * Copyright (C) 2019 - 2022 MicroStream Software
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import static one.microstream.X.notNull;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import one.microstream.chars.ObjectStringAssembler;
import one.microstream.chars.VarString;

/**
 * 
 * @deprecated will be removed in version 8
 * @see one.microstream.storage.configuration
 */
@Deprecated
public interface ConfigurationAssembler extends ObjectStringAssembler<Configuration>
{
	@Override
	public VarString assemble(VarString vs, Configuration configuration);

	@Override
	public default String assemble(final Configuration configuration)
	{
		return ObjectStringAssembler.super.assemble(configuration);
	}


	public static ConfigurationAssembler Ini()
	{
		return new ConfigurationAssembler.IniConfigurationAssembler(
			ConfigurationPropertyAssembler.New()
		);
	}

	public static ConfigurationAssembler Ini(
		final ConfigurationPropertyAssembler propertyAssembler
	)
	{
		return new ConfigurationAssembler.IniConfigurationAssembler(
			notNull(propertyAssembler)
		);
	}

	public static ConfigurationAssembler Xml()
	{
		return new ConfigurationAssembler.XmlConfigurationAssembler(
			ConfigurationPropertyAssembler.New()
		);
	}

	public static ConfigurationAssembler Xml(
		final ConfigurationPropertyAssembler propertyAssembler
	)
	{
		return new ConfigurationAssembler.XmlConfigurationAssembler(
			notNull(propertyAssembler)
		);
	}


	public static class IniConfigurationAssembler implements ConfigurationAssembler
	{
		final ConfigurationPropertyAssembler propertyAssembler;

		IniConfigurationAssembler(
			final ConfigurationPropertyAssembler propertyAssembler
		)
		{
			super();
			this.propertyAssembler = propertyAssembler;
		}

		@Override
		public VarString assemble(
			final VarString     vs           ,
			final Configuration configuration
		)
		{
			final Map<String, String> map = this.propertyAssembler.assemble(configuration);
			map.entrySet().forEach(e -> vs.add(e.getKey()).add(" = ").add(e.getValue()).lf());
			return vs;
		}

	}


	public static class XmlConfigurationAssembler implements ConfigurationAssembler
	{
		final ConfigurationPropertyAssembler propertyAssembler;

		XmlConfigurationAssembler(
			final ConfigurationPropertyAssembler propertyAssembler
		)
		{
			super();
			this.propertyAssembler = propertyAssembler;
		}

		@Override
		public VarString assemble(
			final VarString     vs           ,
			final Configuration configuration
		)
		{
			final Map<String, String> map = this.propertyAssembler.assemble(configuration);

			try
			{
				final Document document    = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
				final Element  rootElement = document.createElement("properties");
				document.appendChild(rootElement);

				map.entrySet().forEach(e -> {
					final Element element = document.createElement("property");
					element.setAttribute("name" , e.getKey  ());
					element.setAttribute("value", e.getValue());
					rootElement.appendChild(element);
				});

				final Transformer  transformer = TransformerFactory.newInstance().newTransformer();
				transformer.setOutputProperty(OutputKeys.INDENT, "yes");
				transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
				transformer.transform(
					new DOMSource(document),
					new StreamResult(new VarStringWriter(vs))
				);
			}
			catch(TransformerException | TransformerFactoryConfigurationError | ParserConfigurationException e)
			{
				throw new StorageConfigurationException(e);
			}

			return vs;
		}


		static class VarStringWriter extends Writer
		{
			final VarString vs;

			VarStringWriter(final VarString vs)
			{
				super();
				this.vs = vs;
			}

			@Override
			public void write(final char[] cbuf, final int off, final int len) throws IOException
			{
				this.vs.add(cbuf, off, len);
			}

			@Override
			public void flush() throws IOException
			{
				// no-op
			}

			@Override
			public void close() throws IOException
			{
				// no-op
			}

		}

	}

}
